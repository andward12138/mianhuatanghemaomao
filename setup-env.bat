@echo off
echo 配置Java和Maven环境...

REM 设置Java路径（请修改为实际JDK安装路径）
set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%

REM 设置Maven路径（请修改为实际Maven安装路径）
set MAVEN_HOME=C:\apache-maven-3.9.9
set PATH=%MAVEN_HOME%\bin;%PATH%

REM 验证环境
echo 检查Java版本...
java -version
echo.
echo 检查Maven版本...
mvn -version
echo.
echo 环境配置完成！
echo 现在可以运行：mvn javafx:run
pause

