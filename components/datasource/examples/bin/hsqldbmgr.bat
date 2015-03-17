@echo off
rem
rem Run HSQLDB Manager
rem
rem Author: Leif Mortenson [leif@tanukisoftware.com]

rem
rem Determine if JAVA_HOME is set and if so then use it
rem
if not "%JAVA_HOME%"=="" goto found_java

set EXAMPLE_JAVACMD=java
goto file_locate

:found_java
set EXAMPLE_JAVACMD=%JAVA_HOME%\bin\java

:file_locate

rem
rem Locate where the example is in filesystem
rem
if not "%OS%"=="Windows_NT" goto start

rem %~dp0 is name of current script under NT
set EXAMPLE_HOME=%~dp0

rem : operator works similar to make : operator
set EXAMPLE_HOME=%EXAMPLE_HOME:\bin\=%

:start

if not "%EXAMPLE_HOME%" == "" goto example_home

echo.
echo Warning: EXAMPLE_HOME environment variable is not set.
echo   This needs to be set for Win9x as it's command prompt 
echo   scripting bites
echo.
goto end

:example_home
rem
rem build the runtime classpath
rem
set CP=%EXAMPLE_HOME%\lib\jdbcdatasource.jar


set _LIBJARS=;%EXAMPLE_HOME%\..\commonlib\hsqldb.jar
if not "%_LIBJARS%" == "" goto run

echo Unable to set CLASSPATH.
goto end

:run
set CP=%CP%%_LIBJARS%

rem Run the example application
%EXAMPLE_JAVACMD% -classpath "%CP%" org.hsqldb.util.DatabaseManager

:end
