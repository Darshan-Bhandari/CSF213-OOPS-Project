@echo off
setlocal
cd /d "%~dp0.."
py -3 "%~dp0run_local.py" %*
exit /b %errorlevel%
