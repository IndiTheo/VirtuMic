load("@rules_android//providers:providers.bzl", "AndroidSdkInfo")

def _sdk_proxy_impl(ctx):
    # Retrieve the official auto-discovered SDK info
    base = ctx.attr.base_sdk[AndroidSdkInfo]

    # Return a new provider using a custon jar but ALL other official tools
    android_sdk_info = AndroidSdkInfo(
            android_jar = ctx.file.custom_jar,
            aapt = base.aapt,
            aapt2 = base.aapt2,
            adb = base.adb,
            aidl = base.aidl,
            aidl_lib = base.aidl_lib,
            apk_builder = base.apk_builder,
            apk_signer = base.apk_signer,
            dx = base.dx,
            framework_aidl = base.framework_aidl,
            legacy_main_dex_list_generator = base.legacy_main_dex_list_generator,
            main_dex_classes = base.main_dex_classes,
            main_dex_list_creator = base.main_dex_list_creator,
            proguard = base.proguard,
            shrinked_android_jar = base.shrinked_android_jar,
            source_properties = base.source_properties,
            system = None,
            zip_align = base.zip_align,
            build_tools_version = base.build_tools_version,
    )

    return [
        android_sdk_info,
        platform_common.ToolchainInfo(android_sdk_info = android_sdk_info),
    ]

sdk_proxy = rule(
    implementation = _sdk_proxy_impl,
    attrs = {
        "base_sdk": attr.label(mandatory = True, providers = [AndroidSdkInfo]),
        "custom_jar": attr.label(allow_single_file = True, mandatory = True),
    },
)