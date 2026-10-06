@rem Minimal Windows launcher for the checked-in Gradle wrapper.
@echo off
setlocal
set DIRNAME=%~dp0
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
