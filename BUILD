load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
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

gerrit_plugin_tests(
    name = "rename-project_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["rename-project"],
    deps = [":rename-project__plugin"],
)
