FROM java:8
WORKDIR oliveryky/Homework/6011/FirstServer/
ADD target/FirstServer-1.0-SNAPSHOT-shaded.jar ChatServer.jar
ADD target/resources/ resources/
ADD chatHistory.db chatHistory.db
EXPOSE 8080
CMD java -jar ChatServer.jar