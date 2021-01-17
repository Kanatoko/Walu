#!/bin/sh

ant
java -Xmx21G -Xms21G -XX:+UseG1GC -cp bin/:lib/* net.jumperz.app.MWalu.MWalu data/access.log
