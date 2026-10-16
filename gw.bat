@echo off
rem Build env for this project only.
rem Real folder is ASCII D:\bmt-nosecalc (Korean paths break AGP and the test runner).
rem The folder under D:\(workspace)\cnc-calc is a junction to it.
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
set "ANDROID_HOME=D:\android-sdk"
set "GRADLE_USER_HOME=D:\android-sdk\gradle-home"
cd /d D:\bmt-nosecalc
call D:\bmt-nosecalc\gradlew.bat %*
