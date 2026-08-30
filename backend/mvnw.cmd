@ECHO OFF
SETLOCAL

SET MAVEN_VERSION=3.9.9
SET WRAPPER_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%
SET MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%
SET MVN_EXE=%MAVEN_HOME%\bin\mvn.cmd

IF NOT EXIST "%MVN_EXE%" (
  ECHO Maven not found locally, downloading apache-maven-%MAVEN_VERSION% ...
  IF NOT EXIST "%WRAPPER_DIR%" MKDIR "%WRAPPER_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%WRAPPER_DIR%\maven.zip'; Expand-Archive -Path '%WRAPPER_DIR%\maven.zip' -DestinationPath '%WRAPPER_DIR%' -Force; Remove-Item '%WRAPPER_DIR%\maven.zip'"
  IF NOT EXIST "%MVN_EXE%" (
    ECHO Failed to download/install Maven. Check your internet connection.
    EXIT /B 1
  )
)

CALL "%MVN_EXE%" %*
EXIT /B %ERRORLEVEL%
