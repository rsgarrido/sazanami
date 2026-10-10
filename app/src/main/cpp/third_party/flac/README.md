# Official libFLAC 1.5.0 — Session 9 decoder subset

Upstream: https://github.com/xiph/flac/releases/tag/1.5.0
Source archive: https://github.com/xiph/flac/releases/download/1.5.0/flac-1.5.0.tar.xz
Official release SHA-256: f2c1c76592a82ffff8413ba3c4a1299b6c7ab06c734dee03fd88630485c2b920.

The archive is retained locally. The files under upstream/ were extracted unchanged
from that archive, with Python tarfile (no upstream script/tool was executed).
UPSTREAM_SHA256.txt lists every extracted file. CMake verifies both the local
archive and all extracted-file checksums. Configuration/build/runtime need no
network download. Updating libFLAC requires an explicit version/provenance/manifest
update; no floating branch, submodule, or FetchContent URL is used.

Compiled C translation units: bitmath.c, bitreader.c, cpu.c, crc.c, fixed.c,
float.c, format.c, lpc.c, md5.c, memory.c, stream_decoder.c. The metadata_object.c source
and additional upstream public/private/share headers are retained for provenance,
but metadata_object.c, encoders, bitwriter, windows, Ogg, SIMD/assembly,
libFLAC++, utilities, docs and upstream tests are not built.

The decoder subset also retains the required textual source fragment
src/libFLAC/deduplication/bitreader_read_rice_signed_block.c, extracted unchanged
from the same archive and covered by UPSTREAM_SHA256.txt. bitreader.c includes it
twice with upstream's distinct function definitions; it must not be compiled as
a separate translation unit. Its path resolves relative to bitreader.c without
additional include directories.

The vendoring correction audited all ten selected translation units and their
transitive local includes (33 upstream files including this fragment). No other
required local file was missing. lpc.c's references to
deduplication/lpc_compute_autocorrelation_intrin.c are inside the block excluded by
FLAC__INTEGER_ONLY_LIBRARY; that floating-point fragment and its SIMD siblings are
not required by this target and were not extracted. The manifest now covers all
73 retained upstream files after the textual-include correction. Decoder sources,
version, flags and CMake include paths/translation-unit list were not changed by
that correction.

The subsequent link-dependency correction adds only unchanged upstream float.c,
bringing the manifest to 74 files and the target to eleven translation units.
In this release, float.c's implementation is under FLAC__INTEGER_ONLY_LIBRARY:
it defines FLAC__FP_ONE, FLAC__FP_LN2 and FLAC__fixedpoint_log2 (plus the remaining
fixed-point constants) using integer arithmetic. fixed.c contains the decoder's
restore functions as well as integer predictor helpers, so pulling its object
into the decoder link requires this support even without an encoder target.
The filename does not mean PCM passes through floating point.

Read-only inspection of the ten existing human-built arm64 decoder ELF objects
found all ten in stream_decoder.c's object dependency closure. Their only
unresolved FLAC symbols were those three fixed-point symbols, all defined by the
pinned archive's float.c. That source has no additional external libFLAC function
dependency: its lookup tables are local and its assertion/integer-literal helpers
are header macros. No further source, linker suppression or feature change was
needed by this static audit. The corrected target has not been built by Codex.

The custom Android/NDK CMake target uses C99, PIC, FLAC__NO_DLL,
FLAC__INTEGER_ONLY_LIBRARY, FLAC__NO_ASM, HAVE_CONFIG_H and 64-bit file offsets.
config.h.in is Sazanami-owned build configuration: no Ogg or SIMD, portable 32-bit
bitreader words, Android API 26+ POSIX/standard integer headers and fseeko support.
This avoids running the upstream all-components CMake project or linking encoder
threading dependencies. It compiles upstream generic integer reconstruction and
official MD5 logic. No sample passes through float.

License: libFLAC is BSD-3-Clause/Xiph (upstream/COPYING.Xiph). Copyright headers
are retained, including the MD5 source's own public-domain notice. Upstream
COPYING.GPL, COPYING.LGPL and AUTHORS are also preserved; they describe other
upstream components and do not change Sazanami's GPLv3 license. The binary
distribution includes app/src/main/assets/third_party_notices/libFLAC-COPYING.Xiph.

API/source audit for this pinned release:
- include/FLAC/stream_decoder.h documents set_md5_checking before init,
  get_md5_checking as the requested setting rather than internal active checking,
  and finish(false) on nonzero STREAMINFO MD5 mismatch.
- src/libFLAC/stream_decoder.c finish finalizes the internal MD5, compares only
  when do_md5_checking is active, frees decoder buffers and resets settings/state.
- The STREAMINFO parser disables do_md5_checking when the 16 MD5 bytes are zero.
  Missing STREAMINFO and seeking also prevent a valid reference check.
- The error callback covers corruption/header/CRC/metadata errors; MD5 mismatch is
  not an error-callback code. Record finish's boolean separately.
- init_stream with null seek callback supports sequential providers. Sazanami uses
  read, optional logical tell/known length, write/metadata/error callbacks; no seek,
  flush, reset, Ogg initialization or audio output.
- Sazanami ignores all metadata except STREAMINFO, captures decoder EOS state
  before finish resets it, and requires actual provider EOF and consistent PCM
  counts. Any libFLAC error makes the verification terminal; repaired/concealed
  samples are never accepted/hashes never continue after that error.

This source integration has not been compiled or tested by Codex. The human
will build/test it. No full decoder fixture or hardware result is asserted.
