# Third-party notices

Oxman Game Optimizer 1.2.0 distribuye o utiliza los siguientes componentes de terceros. Las licencias incluidas dentro de cada JAR siguen siendo aplicables.

## PresentMon

Source: https://github.com/GameTechDev/PresentMon  
License: MIT

Copyright (C) 2017-2024 Intel Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Runtime libraries

- OpenJFX 22.0.1 — GPL-2.0 with Classpath Exception — https://openjfx.io/
- OSHI 6.6.5 — MIT — https://github.com/oshi/oshi
- JNA and JNA Platform 5.15.0 — LGPL-2.1-or-later or Apache-2.0 — https://github.com/java-native-access/jna
- SLF4J 2.0.16 — MIT — https://www.slf4j.org/
- Apache Commons CSV 1.11.0 — Apache-2.0 — https://commons.apache.org/proper/commons-csv/
- Apache Commons IO 2.16.1 — Apache-2.0 — https://commons.apache.org/proper/commons-io/
- Apache Commons Codec 1.16.1 — Apache-2.0 — https://commons.apache.org/proper/commons-codec/

## CPU temperature helper

`tools/hardware/OxmanHardwareSensor.exe` is a read-only, self-contained .NET 8
helper used to query CPU temperature sensors on Windows. It does not install
PawnIO or another driver. If the optional PawnIO driver is absent, the helper
returns `PAWNIO_NOT_INSTALLED` and Oxman displays `Temperatura: N/D`.

The resolved win-x64 publish graph includes:

- LibreHardwareMonitorLib 0.9.6 — MPL-2.0 — source commit `3d331e3370efb858411f19511373eff65a218701`
- BlackSharp.Core 1.0.7 — MPL-2.0 — source commit `c70b735c6cec123ee8a046ac4a0bc6c606f52cf0` — copyright Florian K.
- DiskInfoToolkit 1.1.2 — MPL-2.0 — source commit `25319eae5781e75bcf141e844ceab2afe94d40ea` — copyright Florian K.
- RAMSPDToolkit-NDD 1.4.2 — MPL-2.0 — source commit `3b47b960e0830fef344624ad5e389675d5f0a1ce` — copyright Florian K.
- HidSharp 2.6.4 — Apache-2.0 — copyright 2010-2025 James F. Bellinger
- Mono.Posix.NETStandard 1.0.0, including its win-x64 native helpers — official legacy license file referenced by its NuGet metadata
- System.CodeDom 10.0.2, System.IO.Ports 10.0.3, System.Management 10.0.2 and System.Threading.AccessControl 10.0.3 — MIT — Microsoft Corporation
- Microsoft .NET 8 self-contained runtime — MIT and bundled third-party notices

The corresponding license texts, package metadata notes and pinned source URLs
are distributed under `tools/hardware/licenses/`. Oxman does not modify the
MPL-covered libraries. Their Source Code Form is available at the pinned commits
recorded in each package directory.

Mono.Posix.NETStandard 1.0.0 does not declare an SPDX expression in its NuGet
metadata. Its official license URL redirects to the composite Mono `LICENSE`;
that file and `PATENTS.TXT` are included verbatim. Applicability of the composite
license to the exact managed and native 1.0.0 files requires additional review
before commercial distribution.

No dependencias de test se distribuyen en `standalone-libs`.
