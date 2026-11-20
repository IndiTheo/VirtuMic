def compute_version_code(version_str):
    """Computes versionCode that respects SemVer for the overlay module"""
    clean = version_str.lstrip("v").split("-")[0]
    parts = clean.split(".")
    if len(parts) >= 3:
        major = int(parts[0])
        minor = int(parts[1])
        patch = int(parts[2])
        return (major * 1000000) + (minor * 1000) + patch
    return 100000

def _module_prop_impl(ctx):
    version_code = compute_version_code(ctx.attr.version)
    formatted_version = ctx.attr.version if ctx.attr.version.startswith("v") else "v" + ctx.attr.version

    update_url = ctx.var.get("UPDATE_JSON_URL", "")
    update_json_line = "updateJson=" + update_url if update_url else ""

    ctx.actions.expand_template(
        template = ctx.file.template,
        output = ctx.outputs.out,
        substitutions = {
            "{VERSION}": formatted_version,
            "{VERSION_CODE}": str(version_code),
            "{UPDATE_JSON_LINE}": update_json_line,
        },
    )

module_prop = rule(
    implementation = _module_prop_impl,
    attrs = {
        "template": attr.label(mandatory = True, allow_single_file = True),
        "version": attr.string(default = "0.1.0"),
        "out": attr.output(mandatory = True),
    },
)

def _version_code_impl(ctx):
    version_code = compute_version_code(ctx.attr.version)
    ctx.actions.write(output = ctx.outputs.out, content = str(version_code))

version_code_file = rule(
    implementation = _version_code_impl,
    attrs = {
        "version": attr.string(default = "0.1.0"),
        "out": attr.output(mandatory = True),
    },
)
