# MOJA ZMIANY

* Zbudowano w oparciu o aktualne SDK megaApi
* Wyłączono task makeFile jni przez co projekt w ogóle wstaje
* Wykomentowano metody których nie ma w SDK (meganz nie odpisał dlaczego tak jest)

# CO TRZEBA ZROBIC

* Trzeba wyciągnąć bindingi i skompilowane sdk i przykryć to jakimś interfejsem
* Trzeba zrobić jakiś test uploadu i downloadu i takich pierdół

# PROBLEMY

* Nieziemski burdel w kodzie, brakujące metody, komentarze, kopalnia złych praktyk
* NATYWNE SDK które jest bardzo masywne, nie wiem jak go zgrabnie przerzucić na nasz web


MEGA Android Client
================

A fully-featured client to access your Cloud Storage provided by MEGA.

This document will guide you to build the application on a Linux machine with Android Studio.

### Setup development environment

* [Android Studio](http://developer.android.com/intl/es/sdk/index.html)

* [Android SDK Tools](http://developer.android.com/intl/es/sdk/index.html#Other)

* [Android NDK](http://developer.android.com/intl/es/ndk/downloads/index.html)

### Build & Run the application

* Get the source code

```
git clone --recursive https://github.com/meganz/android.git
```

* Configure the variable `NDK_ROOT` to point to your Android NDK installation path at `jni/Makefile`.

* Open the project with Android Studio, let it build the project and hit _*Run*_
