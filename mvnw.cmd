@rem Maven Start Wrapper script for Windows
@rem

@if "%MAVEN_HOME%"=="" set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6-bin
@if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    @echo Downloading Maven...
    @powershell -Command "Invoke-WebRequest -Uri 'https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP%\maven-wrapper.zip'"
    @powershell -Command "Expand-Archive -Path '%TEMP%\maven-wrapper.zip' -DestinationPath '%MAVEN_HOME%\..' -Force"
    @echo Maven downloaded
)
@set "JAVA_HOME=%JAVA_HOME%"
@set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
@mvn %*
