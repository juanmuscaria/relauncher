// SPDX-FileCopyrightText: 2026 juanmuscaria <juan@juanmuscaria.com>
//
// SPDX-License-Identifier: MPL-2.0

class SmoketestEntrypointExtension {
    // 'launchwrapper' | 'modlauncher' | 'fancymodloader' | 'fabric'
    String loader

    // mc version this combo targets, informational
    String mcVersion

    // drives the maven coords of the loader jar
    String loaderVersion

    // gradle path of the relauncher entrypoint module for this loader
    String entrypointModule

    // escape hatch, can't simulate: 'retrofuturagradle' | 'neogradle' | 'loom' | null
    String useRealGradlePlugin

    // smoketest jvm version (e.g. 21), defaults to the gradle jvm when null
    Integer jvmVersion
}
