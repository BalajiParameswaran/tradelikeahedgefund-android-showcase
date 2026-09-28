// Root build file. Plugin versions are declared ONCE here (apply false);
// subproject build files apply them without versions.

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    kotlin("multiplatform") version "2.4.20" apply false
    kotlin("android") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    kotlin("plugin.compose") version "2.4.20" apply false
}
