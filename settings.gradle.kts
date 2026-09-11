// 仓库顺序：官方源在前，阿里云镜像殿后兜底。
//
// 为什么不把镜像放在前面：Gradle 遇到**连接错误**（超时、connection reset）不会
// 自动改用下一个仓库，而是直接让构建失败。镜像在前 = 整个构建的成败取决于
// 每个环境都能连上 maven.aliyun.com。CI 跑在 GitHub 的海外 runner 上，实测就是这样
// 挂在最早期的插件解析阶段的（check-run 报 "Connection reset by peer"）。
// 镜像放在最后仍然能在官方源缺件时兜底，对中国大陆的本地构建也依旧有用。
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}

rootProject.name = "Narvive"
include(":app")
