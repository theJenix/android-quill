#!/bin/sh

pushd /Users/sl/OtherStuff/workspace/Quill

javah -jni -d jni -classpath bin/classes org.libharu.Document
javah -jni -d jni -classpath bin/classes org.libharu.Page
javah -jni -d jni -classpath bin/classes org.libharu.Font
javah -jni -d jni -classpath bin/classes org.libharu.Image


/Users/sl/OtherStuff/android-ndk-r9c/ndk-build

popd
