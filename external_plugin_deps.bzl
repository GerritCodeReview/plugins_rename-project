load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
  maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-core:2.16.0",
    sha1 = "a022ee494c753789a1e7cae75099de81d8a5cea6",
    deps = [
      '@byte-buddy//jar',
      '@objenesis//jar',
    ],
  )

  maven_jar(
    name = "byte-buddy",
    artifact = "net.bytebuddy:byte-buddy:1.7.9",
    sha1 = "51218a01a882c04d0aba8c028179cce488bbcb58",
  )

  maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:2.6",
    sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
  )

