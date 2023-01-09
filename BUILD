load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "rename-project",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: rename-project",
        "Gerrit-Module: com.googlesource.gerrit.plugins.renameproject.Module",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.renameproject.SshModule",
        "Gerrit-HttpModule: com.googlesource.gerrit.plugins.renameproject.HttpModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
)

junit_tests(
    name = "rename-project_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["rename-project"],
    deps = [":rename-project__plugin_test_deps"],
)

java_library(
    name = "rename-project__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":rename-project__plugin",
        "@mockito//jar",
    ],
)
