@echo off
echo =======================================================
echo          KidShield Project Cleanup Utility
echo =======================================================
echo.

echo [1/3] Cleaning up Backend logs and temporary files...
cd c:\Users\baran\OneDrive\Pictures\backend
if exist logcat.txt del /q logcat.txt
if exist error.txt del /q error.txt
if exist error_out.html del /q error_out.html
if exist capture.py del /q capture.py
if exist test_auth_flow.py del /q test_auth_flow.py
if exist test_integration.py del /q test_integration.py
if exist test_reg.py del /q test_reg.py
if exist test_req.py del /q test_req.py
if exist verify_integration.py del /q verify_integration.py
if exist api_documentation.md.resolved del /q api_documentation.md.resolved
if exist INTEGRATION_STATUS.md del /q INTEGRATION_STATUS.md
if exist HOW_TO_RUN_SERVER.txt del /q HOW_TO_RUN_SERVER.txt
if exist fix_backend_db.bat del /q fix_backend_db.bat
if exist start_server.bat del /q start_server.bat

echo [2/4] Cleaning up Android Frontend temporary files...
cd c:\Users\baran\Downloads\Kidshield
if exist build_output.log del /q build_output.log
if exist build_output_cmd.log del /q build_output_cmd.log
if exist fix_android_ui.py del /q fix_android_ui.py

echo [3/4] Cleaning up Web Frontend redundant folders...
cd c:\Users\baran\Downloads\Kidshield\KidShield-Web
if exist css rmdir /s /q css
if exist js rmdir /s /q js

echo [4/4] Synchronizing Backend Database...
cd c:\Users\baran\OneDrive\Pictures\backend
py -3 manage.py makemigrations api
py -3 manage.py migrate

echo.
echo =======================================================
echo 🎉 CLEANUP AND SYNC COMPLETE!
echo Your project is now clean and the database is up-to-date.
echo =======================================================
pause
