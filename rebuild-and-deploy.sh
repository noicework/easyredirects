#!/bin/bash

# Set script to exit on error
set -e

echo "Building easyredirects..."
mvn clean package

echo "Copying JAR to Magnolia Author instance..."
cp target/easyredirects-1.7.1-SNAPSHOT.jar ~/Projects/mmp/apache-tomcat/webapps/magnoliaAuthor/WEB-INF/lib

echo "Shutting down Tomcat..."
~/Projects/mmp/apache-tomcat/bin/shutdown.sh

echo "Waiting 15 seconds for Tomcat to shut down completely..."
sleep 15

echo "Starting Tomcat..."
~/Projects/mmp/apache-tomcat/bin/startup.sh

echo "Deployment complete. Magnolia is starting up..."
echo "Showing log output (press Ctrl+C to stop):"
sleep 15

