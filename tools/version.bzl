def _version_from_env_impl(repository_ctx):
    version = repository_ctx.os.environ.get("VERSION", "0.0.0")
    repository_ctx.file("version.bzl", "VERSION = \"%s\"" % version)
    repository_ctx.file("BUILD.bazel", "")

version_from_env = repository_rule(
    implementation = _version_from_env_impl,
    environ = ["VERSION"],
)

def _version_extension_impl(module_ctx):
    version_from_env(name = "version")

version_extension = module_extension(
    implementation = _version_extension_impl,
)
