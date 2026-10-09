#include <jni.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <linux/usb/ch9.h>

#include <array>
#include <cerrno>

// Uses the installed NDK Linux UAPI directly. No driver implementation, local
// ioctl declarations, fd owner, persistent session, or streaming transport.
namespace {
constexpr jint kApiVersion = 1;
constexpr int kMaxInterfaces = 256;
constexpr int kMaxRecords = 10 + 3 * kMaxInterfaces;
static_assert(sizeof(unsigned int) == 4, "usbfs interface argument must be a UAPI unsigned int");
static_assert(USB_SPEED_UNKNOWN == 0 && USB_SPEED_LOW == 1 && USB_SPEED_FULL == 2 &&
              USB_SPEED_HIGH == 3 && USB_SPEED_WIRELESS == 4 && USB_SPEED_SUPER == 5 &&
              USB_SPEED_SUPER_PLUS == 6, "Update Kotlin speed mapping if the UAPI changes");

struct IoctlResult { int value = 0; int error = 0; };

IoctlResult getDriver(int borrowedFd, usbdevfs_getdriver& binding) noexcept {
    const int value = ::ioctl(borrowedFd, USBDEVFS_GETDRIVER, &binding);
    const int savedError = value < 0 ? errno : 0; // Before any other native/JNI call.
    return {value, savedError};
}

IoctlResult getSpeed(int borrowedFd) noexcept {
    const int value = ::ioctl(borrowedFd, USBDEVFS_GET_SPEED, nullptr);
    const int savedError = value < 0 ? errno : 0;
    return {value, savedError};
}

IoctlResult claimAudioControl(int borrowedFd, unsigned int& number) noexcept {
    const int value = ::ioctl(borrowedFd, USBDEVFS_CLAIMINTERFACE, &number);
    const int savedError = value < 0 ? errno : 0;
    return {value, savedError};
}

IoctlResult releaseAudioControl(int borrowedFd, unsigned int& number) noexcept {
    const int value = ::ioctl(borrowedFd, USBDEVFS_RELEASEINTERFACE, &number);
    const int savedError = value < 0 ? errno : 0;
    return {value, savedError};
}

// Does not own/close the fd. Its scope ends immediately after the single claim;
// no allocation, JNI call, clock request or endpoint operation occurs while owned.
class ImmediateAcRelease {
public:
    ImmediateAcRelease(int fd, unsigned int number, IoctlResult& result, bool& attempted) noexcept
        : fd_(fd), number_(number), result_(result), attempted_(attempted) {}
    ~ImmediateAcRelease() noexcept {
        if (claimed_) {
            attempted_ = true;
            result_ = releaseAudioControl(fd_, number_);
        }
    }
    void markClaimed() noexcept { claimed_ = true; }
    ImmediateAcRelease(const ImmediateAcRelease&) = delete;
    ImmediateAcRelease& operator=(const ImmediateAcRelease&) = delete;
private:
    int fd_;
    unsigned int number_;
    IoctlResult& result_;
    bool& attempted_;
    bool claimed_ = false;
};

const char* errnoName(int number) noexcept {
    switch (number) {
        case 0: return "";
        case EPERM: return "EPERM";
        case ENOENT: return "ENOENT";
        case EINTR: return "EINTR";
        case EIO: return "EIO";
        case EBADF: return "EBADF";
        case EAGAIN: return "EAGAIN";
        case ENOMEM: return "ENOMEM";
        case EACCES: return "EACCES";
        case EBUSY: return "EBUSY";
        case ENODEV: return "ENODEV";
        case EINVAL: return "EINVAL";
        case ENOTTY: return "ENOTTY";
        case ENOSYS: return "ENOSYS";
        case ENODATA: return "ENODATA";
        case EOPNOTSUPP: return "EOPNOTSUPP";
        case ETIMEDOUT: return "ETIMEDOUT";
        case EPIPE: return "EPIPE";
        case EPROTO: return "EPROTO";
        default: return "UNKNOWN";
    }
}

const char* speedName(int raw) noexcept {
    switch (raw) {
        case USB_SPEED_LOW: return "LOW";
        case USB_SPEED_FULL: return "FULL";
        case USB_SPEED_HIGH: return "HIGH";
        case USB_SPEED_WIRELESS: return "WIRELESS";
        case USB_SPEED_SUPER: return "SUPER";
        case USB_SPEED_SUPER_PLUS: return "SUPER_PLUS";
        default: return "UNKNOWN";
    }
}

void invalidArguments(JNIEnv* env) {
    jclass exception = env->FindClass("java/lang/IllegalArgumentException");
    if (exception != nullptr) {
        env->ThrowNew(exception, "Invalid bounded USB diagnostic plan or borrowed fd");
        env->DeleteLocalRef(exception);
    }
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeApiVersion(JNIEnv*, jobject) {
    return kApiVersion;
}

extern "C" JNIEXPORT jobject JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeInspect(
        JNIEnv* env, jobject, jint borrowedFd, jintArray interfaceNumbers, jint audioControlNumber) {
    if (borrowedFd < 0 || interfaceNumbers == nullptr || audioControlNumber < -1 || audioControlNumber > 255) {
        invalidArguments(env);
        return nullptr;
    }
    const jsize count = env->GetArrayLength(interfaceNumbers);
    if (count < 0 || count > kMaxInterfaces) { invalidArguments(env); return nullptr; }
    std::array<jint, kMaxInterfaces> interfaces{};
    if (count > 0) env->GetIntArrayRegion(interfaceNumbers, 0, count, interfaces.data());
    if (env->ExceptionCheck()) return nullptr;
    std::array<bool, kMaxInterfaces> seen{};
    for (jsize i = 0; i < count; ++i) {
        const jint number = interfaces[i];
        if (number < 0 || number > 255 || seen[number]) { invalidArguments(env); return nullptr; }
        seen[number] = true;
    }
    if (audioControlNumber >= 0 && !seen[audioControlNumber]) { invalidArguments(env); return nullptr; }

    std::array<usbdevfs_getdriver, kMaxInterfaces> bindings{};
    std::array<IoctlResult, kMaxInterfaces> driverResults{};
    for (jsize i = 0; i < count; ++i) {
        bindings[i].interface = static_cast<unsigned int>(interfaces[i]);
        driverResults[i] = getDriver(borrowedFd, bindings[i]);
        bindings[i].driver[USBDEVFS_MAXDRIVERNAME] = '\0';
    }
    const IoctlResult speed = getSpeed(borrowedFd);
    IoctlResult claim{};
    IoctlResult release{};
    const bool claimAttempted = audioControlNumber >= 0;
    bool releaseAttempted = false;
    if (claimAttempted) {
        unsigned int ac = static_cast<unsigned int>(audioControlNumber);
        {
            ImmediateAcRelease guard(borrowedFd, ac, release, releaseAttempted);
            claim = claimAudioControl(borrowedFd, ac); // Exactly one non-forced claim.
            if (claim.value >= 0) guard.markClaimed();
        } // Immediately attempt release, before packet conversion or any other operation.
    }

    // All native ownership has ended. JNI allocation failure cannot skip release.
    std::array<jint, kMaxRecords> records{};
    records[0] = kApiVersion;
    records[1] = count;
    for (jsize i = 0; i < count; ++i) {
        records[2 + i * 3] = interfaces[i];
        records[3 + i * 3] = driverResults[i].value;
        records[4 + i * 3] = driverResults[i].error;
    }
    const int speedOffset = 2 + count * 3;
    records[speedOffset] = speed.value;
    records[speedOffset + 1] = speed.error;
    records[speedOffset + 2] = claimAttempted ? 1 : 0;
    records[speedOffset + 3] = claim.value;
    records[speedOffset + 4] = claim.error;
    records[speedOffset + 5] = releaseAttempted ? 1 : 0;
    records[speedOffset + 6] = release.value;
    records[speedOffset + 7] = release.error;

    jclass resultClass = env->FindClass("io/github/rsgarrido/sazanami/player/usb/UsbFsNativeResult");
    if (resultClass == nullptr) return nullptr;
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "([I[Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)V");
    if (constructor == nullptr) return nullptr;
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;
    jintArray numbers = env->NewIntArray(10 + count * 3);
    if (numbers == nullptr) return nullptr;
    env->SetIntArrayRegion(numbers, 0, 10 + count * 3, records.data());
    if (env->ExceptionCheck()) return nullptr;
    jobjectArray drivers = env->NewObjectArray(count, stringClass, nullptr);
    if (drivers == nullptr) return nullptr;
    jobjectArray errors = env->NewObjectArray(count + 3, stringClass, nullptr);
    if (errors == nullptr) return nullptr;
    for (jsize i = 0; i < count; ++i) {
        jstring driver = env->NewStringUTF(driverResults[i].value >= 0 ? bindings[i].driver : "");
        if (driver == nullptr) return nullptr;
        env->SetObjectArrayElement(drivers, i, driver);
        env->DeleteLocalRef(driver);
        if (env->ExceptionCheck()) return nullptr;
    }
    for (jsize i = 0; i < count + 3; ++i) {
        const int error = i < count ? driverResults[i].error :
            (i == count ? speed.error : (i == count + 1 ? claim.error : release.error));
        jstring name = env->NewStringUTF(errnoName(error));
        if (name == nullptr) return nullptr;
        env->SetObjectArrayElement(errors, i, name);
        env->DeleteLocalRef(name);
        if (env->ExceptionCheck()) return nullptr;
    }
    jstring speedLabel = env->NewStringUTF(speed.value >= 0 ? speedName(speed.value) : "UNKNOWN");
    if (speedLabel == nullptr) return nullptr;
    return env->NewObject(resultClass, constructor, numbers, drivers, errors, speedLabel);
}
