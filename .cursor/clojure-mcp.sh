#!/bin/bash
cd /home/jba/PROJECTS/spell-checker && clojure -Sdeps "{:deps {org.slf4j/slf4j-nop {:mvn/version,\"2.0.16\"},clojure-mcp/clojure-mcp {:local/root,\"/home/jba/REPO/clojure-mcp\"}} :aliases {:mcp {:exec-fn clojure-mcp.main/start-mcp-server :exec-args {:port 7888}}}}" -X:mcp :port $(cat /home/jba/PROJECTS/spell-checker/.nrepl-port)
