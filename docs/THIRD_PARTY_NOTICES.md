# Third-party notices

Sazanami's root LICENSE remains GPLv3.

## libFLAC 1.5.0

Official Xiph.Org upstream: https://github.com/xiph/flac/releases/tag/1.5.0.
Sazanami uses a vendored subset of libFLAC 1.5.0 for experimental native USB Direct FLAC playback. 
Only the necessary decoder components are compiled; the encoder, Ogg support, libFLAC++ library, and command-line utilities are not included.

The upstream BSD-3-Clause/Xiph license is preserved at
app/src/main/cpp/third_party/flac/upstream/COPYING.Xiph and distributed in the APK
as app/src/main/assets/third_party_notices/libFLAC-COPYING.Xiph.
Original per-file notices are retained, including the MD5 implementation's
public-domain notice. Upstream COPYING.GPL, COPYING.LGPL and AUTHORS are retained
with the source archive/subset for provenance.

See app/src/main/cpp/third_party/flac/README.md for the pinned archive checksum,
extracted-source manifest, compiled translation units and offline build decisions.
