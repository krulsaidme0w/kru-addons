# Build provenance

The independent build contract is adapted from this repository's tools-addon:
Gradle 8.8, Fabric Loom 1.7.4, Java 21, Minecraft 1.21.1, Yarn 1.21.1+build.3,
Fabric Loader 0.16.14, official external Meteor Client 0.5.8, JUnit 5.10.2 and
ASM 9.6 (tests only). Names, version and addon metadata are adapted for Builder;
tests inspect the remapped archive and both archives include these notices.
No Tools feature implementation, legacy Blueprint source or other addon code
is copied or imported.

The Gradle wrapper JAR and launchers are byte-identical to tools-addon. They
originate from Gradle (https://github.com/gradle/gradle), copyright the original
authors, Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
Launcher notices are preserved. Wrapper JAR SHA-256:
`d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd`.

The GPL license text is copied unchanged from tools-addon/LICENSE. Builder's
original sources are GPL-3.0-or-later. Meteor Client remains an external GPL
dependency (https://github.com/MeteorDevelopment/meteor-client); Minecraft,
Fabric and test/build libraries are not bundled in the addon archive.
