# kettle-check

Target a Kettle Job (.kjb) file to check all the \<filename> nodes if they point to existing .kjb or .ktr files correctly; if they do, checking will continue recursively.

It should help find mistakes earlier.

## Build
- You need to [install Clojure](https://clojure.org/guides/install_clojure) and Java.
- `clj -T:build uber`

## Use
- `java -jar %PATH_TO_KETTLE_CHECK_JAR% %1 %2`
    - `%1`: KETTLE-WORKFLOW-ROOT, variable references `${KETTLE_WORKFLOW_ROOT}` in \<filename> nodes will be replaced by it
    - `%2`: TARGET-KJB, path of the target kettle job file

> Hidden job entries or steps are also checked, so there can be false alarms.