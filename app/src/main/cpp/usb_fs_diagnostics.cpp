#include <jni.h>
#include "direct_pcm_format.h"
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include <linux/usb/ch9.h>

#include <array>
#include <cerrno>

// Uses the installed NDK Linux UAPI directly. No driver implementation, local
// ioctl declarations, fd owner, persistent session, or streaming transport.
namespace {
constexpr jint kApiVersion = 14;
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

bool validAudioPair(jint ac, jint as) noexcept {
    return ac >= 0 && ac <= 255 && as >= 0 && as <= 255 && ac != as;
}

jobject controlPacket(JNIEnv* env, int length, int returned, int savedError, const jbyte* bytes, int operation = 1) {
    // Operation 2 is GET_INTERFACE: never read its response storage for a non-exact return.
    const int actual = operation == 2 ? (returned == 1 ? 1 : 0) :
        (returned > 0 ? (returned < length ? returned : length) : 0);
    jclass resultClass = env->FindClass("io/github/rsgarrido/sazanami/player/usb/UsbFsNativeControlResult");
    if (resultClass == nullptr) return nullptr;
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(IIILjava/lang/String;[BIII)V");
    if (constructor == nullptr) return nullptr;
    jstring errorName = env->NewStringUTF(errnoName(savedError));
    if (errorName == nullptr) return nullptr;
    jbyteArray raw = env->NewByteArray(actual);
    if (raw == nullptr) return nullptr;
    if (actual > 0) env->SetByteArrayRegion(raw, 0, actual, bytes);
    if (env->ExceptionCheck()) return nullptr;
    return env->NewObject(resultClass, constructor, length, returned, savedError, errorName, raw,
        kApiVersion, operation, actual);
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


// Session 5: one synchronous, borrowed-fd endpoint-zero class READ. The JNI
// boundary independently rejects writes, other selectors and unbounded lengths.
extern "C" JNIEXPORT jobject JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeReadClock(
        JNIEnv* env, jobject, jint borrowedFd, jint requestType, jint request,
        jint value, jint index, jint length) {
    const bool frequencyCur = request == 0x01 && value == 0x0100 && length == 4;
    const bool validityCur = request == 0x01 && value == 0x0200 && length == 1;
    const bool frequencyRange = request == 0x02 && value == 0x0100 &&
        (length == 2 || (length >= 14 && length <= 770 && (length - 2) % 12 == 0));
    if (borrowedFd < 0 || requestType != 0xA1 || index < 0x0100 || index > 0xffff ||
        !(frequencyCur || validityCur || frequencyRange)) {
        invalidArguments(env);
        return nullptr;
    }
    std::array<jbyte, 770> bytes{};
    usbdevfs_ctrltransfer control{};
    control.bRequestType = static_cast<__u8>(requestType);
    control.bRequest = static_cast<__u8>(request);
    control.wValue = static_cast<__u16>(value);
    control.wIndex = static_cast<__u16>(index);
    control.wLength = static_cast<__u16>(length);
    control.timeout = 1000;
    control.data = bytes.data();
    const int returned = ::ioctl(borrowedFd, USBDEVFS_CONTROL, &control);
    const int savedError = returned < 0 ? errno : 0; // Immediately, before JNI/allocation.
    // Never closes, duplicates or retains borrowedFd. Android owns its lifetime.
    return controlPacket(env, length, returned, savedError, bytes.data());
}

// Session 6 standard GET_INTERFACE. No arbitrary standard request API.
extern "C" JNIEXPORT jobject JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeGetAudioStreamingInterface(
        JNIEnv* env, jobject, jint borrowedFd, jint acNumber, jint asNumber) {
    if (borrowedFd < 0 || !validAudioPair(acNumber, asNumber)) { invalidArguments(env); return nullptr; }
    std::array<jbyte, 1> bytes{};
    usbdevfs_ctrltransfer control{};
    control.bRequestType = 0x81;
    control.bRequest = 0x0A;
    control.wValue = 0;
    control.wIndex = static_cast<__u16>(asNumber);
    control.wLength = 1;
    control.timeout = 1000;
    control.data = bytes.data();
    const int returned = ::ioctl(borrowedFd, USBDEVFS_CONTROL, &control);
    const int savedError = returned < 0 ? errno : 0;
    return controlPacket(env, 1, returned, savedError, bytes.data(), 2);
}

// Only Sampling Frequency CUR, channel zero, selected target rate or fresh original rate.
// Entity/interface/original are supplied solely through Kotlin's validated permit.
extern "C" JNIEXPORT jobject JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeSetClockFrequency(
        JNIEnv* env, jobject, jint borrowedFd, jint acNumber, jint asNumber, jint entityId,
        jlong originalRateHz, jlong requestedRateHz) {
    if (borrowedFd < 0 || !validAudioPair(acNumber, asNumber) || entityId < 1 || entityId > 255 ||
        originalRateHz < 1 || originalRateHz > 0xffffffffLL ||
        requestedRateHz < 1 || requestedRateHz > 0xffffffffLL ||
        (!sazanami::directpcm::supportsRate(static_cast<uint32_t>(requestedRateHz)) && requestedRateHz != originalRateHz)) { invalidArguments(env); return nullptr; }
    std::array<jbyte, 4> bytes{};
    for (int i = 0; i < 4; ++i) bytes[i] = static_cast<jbyte>((requestedRateHz >> (8 * i)) & 0xff);
    usbdevfs_ctrltransfer control{};
    control.bRequestType = 0x21;
    control.bRequest = 0x01;
    control.wValue = 0x0100;
    control.wIndex = static_cast<__u16>((entityId << 8) | acNumber);
    control.wLength = 4;
    control.timeout = 1000;
    control.data = bytes.data();
    const int returned = ::ioctl(borrowedFd, USBDEVFS_CONTROL, &control);
    const int savedError = returned < 0 ? errno : 0;
    return controlPacket(env, 4, returned, savedError, bytes.data(), 3);
}

// No detach/reset operation: Android already owns AS on this borrowed fd.
// Only the descriptor-resolved idle or selected PCM target alternate is accepted.
extern "C" JNIEXPORT jobject JNICALL
Java_io_github_rsgarrido_sazanami_player_usb_UsbFsDiagnostics_nativeSetAudioStreamingAlternate(
        JNIEnv* env, jobject, jint borrowedFd, jint acNumber, jint asNumber,
        jint targetAlternate, jint idleAlternate, jint requestedAlternate) {
    if (borrowedFd < 0 || !validAudioPair(acNumber, asNumber) || targetAlternate < 1 || targetAlternate > 255 ||
        idleAlternate < 0 || idleAlternate > 255 || idleAlternate == targetAlternate ||
        (requestedAlternate != idleAlternate && requestedAlternate != targetAlternate)) { invalidArguments(env); return nullptr; }
    usbdevfs_setinterface selection{};
    selection.interface = static_cast<unsigned int>(asNumber);
    selection.altsetting = static_cast<unsigned int>(requestedAlternate);
    const int returned = ::ioctl(borrowedFd, USBDEVFS_SETINTERFACE, &selection);
    const int savedError = returned < 0 ? errno : 0;
    jclass resultClass = env->FindClass("io/github/rsgarrido/sazanami/player/usb/UsbFsNativeIoctlResult");
    if (resultClass == nullptr) return nullptr;
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(IILjava/lang/String;)V");
    if (constructor == nullptr) return nullptr;
    jstring errorName = env->NewStringUTF(errnoName(savedError));
    if (errorName == nullptr) return nullptr;
    return env->NewObject(resultClass, constructor, returned, savedError, errorName);
}
