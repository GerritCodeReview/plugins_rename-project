load("//tools/bzl:maven_jar.bzl", "maven_jar")

def deps():
    maven_jar(
        name = "mockito",
        artifact = "org.mockito:mockito-core:5.6.0",
        sha1 = "550b7a0eb22e1d72d33dcc2e5ef6954f73100d76",
        deps = [
            "@bytebuddy//jar",
            "@bytebuddy-agent//jar",
            "@objenesis//jar",
        ],
    )

    BYTE_BUDDY_VERSION = "1.14.9"

    maven_jar(
        name = "bytebuddy",
        artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
        sha1 = "b69e7fff6c473d3ed2b489cdfd673a091fd94226",
    )

    maven_jar(
        name = "bytebuddy-agent",
        artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
        sha1 = "dfb8707031008535048bad2b69735f46d0b6c5e5",
    )

    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:3.0.1",
        sha1 = "11cfac598df9dc48bb9ed9357ed04212694b7808",
    )
