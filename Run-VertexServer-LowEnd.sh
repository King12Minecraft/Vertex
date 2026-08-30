#!/bin/bash
# Vertex - Server Launcher (low-end friendly)
# See the .bat version's comments for why each flag is here.
java -Xmx192m -XX:+UseSerialGC -Xss256k -jar VertexServer.jar
