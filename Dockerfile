FROM gradle:8.8-jdk21-alpine AS builder

COPY . /temp
WORKDIR /temp

RUN gradle build -x test

FROM alpine/java:21-jdk AS runner

COPY --from=builder /temp/app/build/libs/bot.jar /bot.jar

CMD java -jar bot.jar
