
# Start a local SQL Server in a Docker container


><code>docker run -d --name sqlserver -e ACCEPT_EULA=1 -e MSSQL_SA_PASSWORD=YourStrongPassw0rd -p 1433:1433 mcr.microsoft.com/azure-sql-edge:latest</code>
