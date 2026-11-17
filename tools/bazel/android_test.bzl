load("@bazel_skylib//lib:paths.bzl", "paths")
load("@rules_android//rules:rules.bzl", "android_local_test")
load("@rules_kotlin//kotlin:android.bzl", "kt_android_library")

def android_test_suite(
        name,
        srcs,
        custom_package,
        manifest,
        deps = [],
        associates = [],
        tags = [],
        visibility = None):
    test_targets = []

    for src in srcs:
        # Isolate class name
        base_name = paths.split_extension(paths.basename(src))[0]

        # Derive test_class Java/Kotlin package path
        path_segments = src.split("/")
        root_index = -1
        for root in ["kotlin", "java"]:
            if root in path_segments:
                root_index = path_segments.index(root) + 1
                break

        if root_index == -1:
            fail("Source file {} must reside under 'java' or 'kotlin' directory.".format(src))

        package_segments = path_segments[root_index:-1]
        package_segments.append(base_name)
        test_class = ".".join(package_segments)

        lib_target_name = "{}_{}_lib".format(name, base_name)
        test_target_name = "{}_{}".format(name, base_name)

        # Compile isolated micro-library
        kt_android_library(
            name = lib_target_name,
            srcs = [src],
            custom_package = custom_package,
            manifest = manifest,
            associates = associates,
            deps = deps,
            visibility = ["//visibility:private"],
            tags = tags,
        )

        # Generate standalone android_local_test
        android_local_test(
            name = test_target_name,
            test_class = test_class,
            manifest = manifest,
            custom_package = custom_package,
            tags = tags,
            visibility = visibility,
            deps = [
                ":" + lib_target_name,
                "@rules_robolectric//bazel:android-all",
            ],
        )

        test_targets.append(":" + test_target_name)

    # Aggregate into native test_suite
    native.test_suite(
        name = name,
        tests = test_targets,
        tags = tags,
        visibility = visibility,
    )
