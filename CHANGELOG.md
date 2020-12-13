## Changelog

Version 2.71 and newer
------

No longer tracked in this file. See [GitHub releases](https://github.com/jenkinsci/support-core-plugin/releases) instead.

### Version 2.70 (2020 Aug 13)

-   [JENKINS-62844](https://issues.jenkins-ci.org/browse/JENKINS-62844) - Update the link to the bundle anonymization setting
-   [JENKINS-62526](https://issues.jenkins-ci.org/browse/JENKINS-62526) - Split JenkinsLogs

### Version 2.69 (2020 Jun 24)

-   Upgrade parent pom to 4.2
-   [JENKINS-62297](https://issues.jenkins-ci.org/browse/JENKINS-62297) - Collect User counts
-   Test improvement - Cleanup formatting and other small things
-   Test improvement - Consistent static imports for assertions
-   Test improvement - Remove non-required Javadoc on test methods
-   Test improvement - Consistent naming of JenkinsRule variable
-   Update changelog

### Version 2.68 (2020 Apr 8)
-   [JENKINS-36929](https://issues.jenkins-ci.org/browse/JENKINS-36929) - Collect agent protocols information
-   [JENKINS-59498](https://issues.jenkins-ci.org/browse/JENKINS-59498) - Improve computation performance
-   [JENKINS-60009](https://issues.jenkins-ci.org/browse/JENKINS-60009) - Add support for Java 9+ for GCLogs
-   [JENKINS-61659](https://issues.jenkins-ci.org/browse/JENKINS-61659) - Allow generating custom support bundle through API
-   [JENKINS-61796](https://issues.jenkins-ci.org/browse/JENKINS-61796) - Add JCasC compatibility. See [CasC configuration](README.md#casc) to get more details
-   Parent POM and dependencies updated. Fixed an InputStream leak and tests improved
-   "Security" as category for the management link

### Version 2.67 (2020 Jan 18)

-   [JENKINS-60805](https://issues.jenkins-ci.org/browse/JENKINS-60805) - Fix backward incompatible change introduced in the previous release 

### Version 2.66 (2020 Jan 14)

-   [JENKINS-59342](https://issues.jenkins-ci.org/browse/JENKINS-59342) - Administrators are able to download contents for Builds, Items and Nodes

### Version 2.65 (2019 Dec 4)

-   [JENKINS-59455](https://issues.jenkins-ci.org/browse/JENKINS-59455) - Fix the checking of env vars using an existing env var instead of path

### Version 2.64 (2019 Nov 21)

-   [Fix security
    issues](https://jenkins.io/security/advisory/2019-11-21/#SECURITY-1634)
    
### Version 2.63 (2019 Nov 4)

-   [JENKINS-59696](https://issues.jenkins-ci.org/browse/JENKINS-59696) - Fix ContentFilters dynamic loading issue

-   [JENKINS-59714](https://issues.jenkins-ci.org/browse/JENKINS-59714) - Remove the pinned/unpinned information

### Version 2.62 (2019 Oct 4)

-   [JENKINS-58165](https://issues.jenkins-ci.org/browse/JENKINS-58165) - Handle parameterized GC logs
    filenames
-   [
    JENKINS-56245](https://issues.jenkins-ci.org/browse/JENKINS-56245) -
    Add instance ID to AboutJenkins
-   [
    JENKINS-58980](https://issues.jenkins-ci.org/browse/JENKINS-58980) -
    Only include latest GC logs
-   [
    JENKINS-59453](https://issues.jenkins-ci.org/browse/JENKINS-59453) -
    Clean up plugin dependencies
-   [
    JENKINS-59554](https://issues.jenkins-ci.org/browse/JENKINS-59554) -
    Wait 3 minutes before generating a
    first support bundle

-   LogFlusher should run before shutting down Jenkins. (PR
    [\#193](https://github.com/jenkinsci/support-core-plugin/pull/193))
-   More gracefully handle a configuration file deleted while the bundle
    is being written (PR
    [\#190](https://github.com/jenkinsci/support-core-plugin/pull/190))

### Version 2.61 (2019 Sept 9)

-   [
    JENKINS-58528](https://issues.jenkins-ci.org/browse/JENKINS-58528) -
    Report available processes for
    master

### Version 2.60 (2019 Aug 6)

-   [
    JENKINS-58528](https://issues.jenkins-ci.org/browse/JENKINS-58528) -
    Fix memory leak caused by nodes
    statistics always growing.

### Version 2.59 (2019 Aug 1)

-   [
    JENKINS-58606](https://issues.jenkins-ci.org/browse/JENKINS-58606) - Add info about backup plugins to
    support bundle.

### Version 2.58 (2019 Jul 16)

-   [
    JENKINS-58393](https://issues.jenkins-ci.org/browse/JENKINS-58393) -
    Avoid the bundle not being
    generated properly when getTime() failed.
-   [
    JENKINS-57602](https://issues.jenkins-ci.org/browse/JENKINS-57602) -
    Fix the agents reporting.

### Version 2.57 (2019 Jul 5)

-   [
    JENKINS-57990](https://issues.jenkins-ci.org/browse/JENKINS-57990) -
    Improve the filtering, avoid
    filtering the garbage collectors logs.

### Version 2.56 (2019 Feb 18)

-   [
    JENKINS-55866](https://issues.jenkins-ci.org/browse/JENKINS-55866) -
    Fix for first implementation.

### Version 2.55 (2019 Feb 18)

-   [
    JENKINS-55014](https://issues.jenkins-ci.org/browse/JENKINS-55014) -
    Prevent deadlock when bundle
    generated at startup.
-   [
    JENKINS-55843](https://issues.jenkins-ci.org/browse/JENKINS-55843) -
    Avoid bundle getting corrupted when
    folder have certain names.
-   [
    JENKINS-55866](https://issues.jenkins-ci.org/browse/JENKINS-55866) -
    Replace only full words.

### Version 2.54 (2019 Jan 15)

-   [
    JENKINS-55493](https://issues.jenkins-ci.org/browse/JENKINS-55493) -
    Update parent POM to test plugin
    with JDK 11.

### Version 2.53 (2018 Dec 11)

-   [
    JENKINS-55493](https://issues.jenkins-ci.org/browse/JENKINS-55493) -
    Update parent POM to test plugin
    with JDK 11.

### Version 2.53 (2018 Dec 11)

-   [
    JENKINS-54922](https://issues.jenkins-ci.org/browse/JENKINS-54922) -
    Improve generated Markdown.
-   [
    JENKINS-54999](https://issues.jenkins-ci.org/browse/JENKINS-54999) -
    Improve anonymization performance.

### Version 2.52 (2018 Nov 28)

-   [
    JENKINS-54688](https://issues.jenkins-ci.org/browse/JENKINS-54688) -
    Information about operating system
    does not get anonymized.

### Version 2.51 (2018 Nov 19)

-   [
    JENKINS-54687](https://issues.jenkins-ci.org/browse/JENKINS-54687) -
    GC Logs should be excluded by
    default.

### Version 2.50 (2018 Sept 13)

-   [JENKINS-53184](https://issues.jenkins-ci.org/browse/JENKINS-53184):
    Use Jenkins version as stop word.

### Version 2.49 (2018 Jul 19)

-   [JENKINS-52639](https://issues.jenkins-ci.org/browse/JENKINS-52639):
    Remove transitive dependency on snakeyaml to fix API compatibility
    issues.

### Version 2.48 (2018 Jul 02)

-   [JENKINS-21670](https://issues.jenkins-ci.org/browse/JENKINS-21670):
    Add support for anonymizing private data in support bundles.
-   [JENKINS-49578](https://issues.jenkins-ci.org/browse/JENKINS-49578):
    Make the button for deleting old support bundles red to help prevent
    users from deleting bundles inadvertently.

### Version 2.47 (2018 Apr 19)

-   [JENKINS-50765](https://issues.jenkins-ci.org/browse/JENKINS-50765) - 
    Harden against XXE vulnerabilities (Just a precaution as this does
    not appear to be exploitable in
    practice
-   [JENKINS-50428](https://issues.jenkins-ci.org/browse/JENKINS-50428) - 
    Support the property in Jenkins 2.114 in newer that allows the
    location of AsyncPeriodicWork task logs to be
    configured
-   [JENKINS-49668](https://issues.jenkins-ci.org/browse/JENKINS-49668) - Fix
    the support bundle component that lists installed root certificate
    authorities

### Version 2.46 (2018 Mar 14)

-   [
    JENKINS-27035](https://issues.jenkins-ci.org/browse/JENKINS-27035) -
    Request/response
    statistics. Tallying command read/write events.

-   **Jenkins baseline updated to 2.107.x.**

### Version 2.45.1 (2018 Mar 09)

-   As has been found abnormal usage of private API, it has been
    reintroduced temporary because of backward compatibility.

### Version 2.45 (2018 Mar 07)

-   [
    JENKINS-49931](https://issues.jenkins-ci.org/browse/JENKINS-49931) -
    Getting issue details... STATUS
    Pass `-Dcom.cloudbees.jenkins.support.impl.HeapUsageHistogram.DISABLED=false`
    to restore heap histogram generation, pending a better fix.
-   Sort thread dumps by name rather than ID.

-   Reduce pressure on master file descriptors.

-   [
    JENKINS-46132](https://issues.jenkins-ci.org/browse/JENKINS-46132) -
    Getting issue details... STATUS
-   **Jenkins baseline updated to 2.46.x.**

### Version 2.44 (2017 Dec 12)

-   `ClassCastException` possible on 2.50+.

-   [
    JENKINS-48436](https://issues.jenkins-ci.org/browse/JENKINS-48436) -
    Getting issue details... STATUS Restoring compatibility for an API
    endpoint.

### Version 2.43

-   [JENKINS-41653](https://issues.jenkins-ci.org/browse/JENKINS-41653) Support
    core warning when collecting data
-   [JENKINS-47779](https://issues.jenkins-ci.org/browse/JENKINS-47779) Could not
    create fully detailed support bundles after 2.86

### Version 2.42

-   [JENKINS-22791](https://issues.jenkins-ci.org/browse/JENKINS-22791) Record
    heap histograms new component

### Version 2.41

-   [JENKINS-44236](https://issues.jenkins-ci.org/browse/JENKINS-44236) Also
    gather NFS related stats
-   ([PR-109](https://github.com/jenkinsci/support-core-plugin/pull/109) non
    functional/user facing change)

### Version 2.40

-   [PR-104](https://github.com/jenkinsci/support-core-plugin/pull/104)
    Delete temp files even if failures happen, and better error message
    if failed to delete the file.
-    [JENKINS-41745](https://issues.jenkins-ci.org/browse/JENKINS-41745) Allow
    SupportCommand to work without a Remoting channel

### Version 2.39

-   [JENKINS-42393](https://issues.jenkins-ci.org/browse/JENKINS-42393)
    Temporary files should not be created in the bundles root directory
    under JENKINS\_HOME
-   [JENKINS-40613](https://issues.jenkins-ci.org/browse/JENKINS-40613)
    Report a blocked Timer
-   [PR-101](https://github.com/jenkinsci/support-core-plugin/pull/101)
    Use UTC for all timestamps

### Version 2.38

-   [JENKINS-40062](https://issues.jenkins-ci.org/browse/JENKINS-40062)
    Optionally include config.xml files for nodes
-   [PR-96](https://github.com/jenkinsci/support-core-plugin/pull/96)
    Print stack trace in logical order
-   [PR-97](https://github.com/jenkinsci/support-core-plugin/pull/97)
    Max file size for agent logs
-   [PR-99](https://github.com/jenkinsci/support-core-plugin/pull/99)
    Add initialization milestone

### Version 2.37

-   [JENKINS-40098](https://issues.jenkins-ci.org/browse/JENKINS-40098)
    Bundle naming strategy should be able to specify an instance type

### Version 2.36

-   [JENKINS-39150](https://issues.jenkins-ci.org/browse/JENKINS-39150)
    Improve remoting channel diagnostics in Support Core
-   [JENKINS-40094](https://issues.jenkins-ci.org/browse/JENKINS-40094)
    Global config.xml only contains a exception
-   [PR
    94](https://github.com/jenkinsci/support-core-plugin/pull/94)
    Fix weird display of the GC Logging gathering
-   [PR
    85](https://github.com/jenkinsci/support-core-plugin/pull/85)
    Add node monitoring data for all nodes (i.e. the columns you
    typically see for each node on the /computer page)

### Version 2.35

-   [PR
    89](https://github.com/jenkinsci/support-core-plugin/pull/89)
    NPE in GCLogs.addContents after 2.34.

### Version 2.34

-   [JENKINS-38572](https://issues.jenkins-ci.org/browse/JENKINS-38572)
    [PR
    76](https://github.com/jenkinsci/support-core-plugin/pull/76)
    Only obtain proxy information if async-http-client plugin is
    installed.
-   [PR
    81](https://github.com/jenkinsci/support-core-plugin/pull/81)
    Fixed some buglets in SupportLogFormatter.
-   [JENKINS-39381](https://issues.jenkins-ci.org/browse/JENKINS-39381)
    [PR
    83](https://github.com/jenkinsci/support-core-plugin/pull/83)
    Add a support component for XML files.
-   [PR
    86](https://github.com/jenkinsci/support-core-plugin/pull/86)
    Check if Jenkins is quieting down.
-   [PR
    87](https://github.com/jenkinsci/support-core-plugin/pull/87)
    Add MIT license.md file.
-   [JENKINS-39607](https://issues.jenkins-ci.org/browse/JENKINS-39607)
    [PR
    88](https://github.com/jenkinsci/support-core-plugin/pull/88)
    GC logs should be collected.
-   **Jenkins baseline updated to 1.625.x**

### Version 2.33

-   [PR
    74](https://github.com/jenkinsci/support-core-plugin/pull/74)
    Add proxy information to update center section.
-   [JENKINS-37772](https://issues.jenkins-ci.org/browse/JENKINS-37772)
    [PR
    73](https://github.com/jenkinsci/support-core-plugin/pull/73)
    Get rid of Initializer(after = InitMilestone.COMPLETED)
-   [PR
    70](https://github.com/jenkinsci/support-core-plugin/pull/70)
    Use require post annotation
-   [PR
    68](https://github.com/jenkinsci/support-core-plugin/pull/68)
    Diagnose startup performance.
-   [PR
    66](https://github.com/jenkinsci/support-core-plugin/pull/66)
    Upgrade parent 2.3 -\> 2.11
-   [JENKINS-34719](https://issues.jenkins-ci.org/browse/JENKINS-34719)
    [PR
    63](https://github.com/jenkinsci/support-core-plugin/pull/63)
    Pick up and logs that have been refactored into JENKINS\_HOME/logs.
-   [PR
    62](https://github.com/jenkinsci/support-core-plugin/pull/62)
    Separate slave logs, and master logs.
-   [JENKINS-26409](https://issues.jenkins-ci.org/browse/JENKINS-26409)
    [PR
    60](https://github.com/jenkinsci/support-core-plugin/pull/60)
    If the stream is terminated early log this information in FINE
    level.

### Version 2.32

-   [PR
    58](https://github.com/jenkinsci/support-core-plugin/pull/58)
    Enable and enforce findbugs on the verify stage.
-   [PR
    53](https://github.com/jenkinsci/support-core-plugin/pull/53)
    Adapt to new parent POM.
-   [JENKINS-34462](https://issues.jenkins-ci.org/browse/JENKINS-34462)
    [PR
    59](https://github.com/jenkinsci/support-core-plugin/pull/59)
    Add CSRF protection status.
-   **Jenkins baseline updated to 1.580.x**

### Version 2.31

-   [PR
    65](https://github.com/jenkinsci/support-core-plugin/pull/56)
    Prevent null pointer exception reporting log level.
-   [PR
    57](https://github.com/jenkinsci/support-core-plugin/pull/57)
    Add the locale of the browser to a slow request.

### Version 2.30

-   [PR
    50](https://github.com/jenkinsci/support-core-plugin/pull/50)
    Add argument to increase max log rotate size.
-   [PR
    51](https://github.com/jenkinsci/support-core-plugin/pull/51)
    Add deadlock test.
-   [PR
    52](https://github.com/jenkinsci/support-core-plugin/pull/52)
    Record log rotate levels.
-   [PR
    48](https://github.com/jenkinsci/support-core-plugin/pull/48)
    Add update center information.
-   [PR
    54](https://github.com/jenkinsci/support-core-plugin/pull/54)
    Add slave version information for master node.
-   [PR
    55](https://github.com/jenkinsci/support-core-plugin/pull/55)
    Switch to a ThreadLocal DateFormatter

### Version 2.29

-   [PR
    44](https://github.com/jenkinsci/support-core-plugin/pull/44)
    Add Jenkins url configuration to validate reverse proxy
    configuration
-   [PR
    43](https://github.com/jenkinsci/support-core-plugin/pull/43)
    Check build queue elements to see if the QueueTaskDispatcher is able
    to run the builds.
-   [JENKINS-30117](https://issues.jenkins-ci.org/browse/JENKINS-30117)
    Support bundle blocks with lots of data
-   [JENKINS-28216](https://issues.jenkins-ci.org/browse/JENKINS-28216)
    Obtain slave logs using async callable.

### Version 2.28

-   [PR
    41](https://github.com/jenkinsci/support-core-plugin/pull/41)
    Deadlock detector now records the full stack traces
-   [PR
    18](https://github.com/jenkinsci/support-core-plugin/pull/18)
    Option to capture the export table of slaves added

### Version 2.27

-   [JENKINS-21668](https://issues.jenkins-ci.org/browse/JENKINS-21668)
    Obtain root CA information.
-   [PR
    24](https://github.com/jenkinsci/support-core-plugin/pull/24)
    Add networking interface information
-   [JENKINS-28876](https://issues.jenkins-ci.org/browse/JENKINS-28876)
    Add user-agent information to slow-request
-   [PR
    37](https://github.com/jenkinsci/support-core-plugin/pull/37)
    -   add system uptime to see if an outage comes from a system reboot
    -   add DMI information to see if Jenkins is running on a
        virtualized server
-   [JENKINS-29034](https://issues.jenkins-ci.org/browse/JENKINS-29034)
    Modify filename to include date and time.

### Version 2.26 Bad release.

### Version 2.25 (June 09 2015)

-   [JENKINS-26409](https://issues.jenkins-ci.org/browse/JENKINS-26409)
    ClientAbortException logged when cancelling a support bundle
    download.
-   [JENKINS-28703](https://issues.jenkins-ci.org/browse/JENKINS-28703)
    Add pinned status to plugin information.

### Version 2.24 (June 03 2015)

-   [JENKINS-24671](https://issues.jenkins-ci.org/browse/JENKINS-24671)
    Added username to slow-requests.
-   [PR
    22](https://github.com/jenkinsci/support-core-plugin/pull/22)
    Add system metrics.

### Version 2.23 (June 03 2015) (Bad release)

### Version 2.22 (May 06 2015)

-   [JENKINS-27669](https://issues.jenkins-ci.org/browse/JENKINS-27669)
    `ClassCircularityError` after creating a log recorder with a blank
    logger name.
-   Limit winsw log file.

### Version 2.21 (Apr 28 2014)

-   Fix Dockerfile generation to prevent overflowing the number of steps
-   Fix download url for plugins in Dockerfile generation
-   Quote thread names in thread dumps
-   Fix HTML title tag to properly display action title ([issue
    \#23278](https://issues.jenkins-ci.org/browse/JENKINS-23278))
-   Add summary of current build queue ([issue
    \#20542](https://issues.jenkins-ci.org/browse/JENKINS-20542))
-   Set file last modified timestamp in ZIP archive
-   Fix generated date format
-   Add load statistics graphs and data to support bundle

### Version 2.20 (Dec 29 2014)

-   [JENKINS-24380](https://issues.jenkins-ci.org/browse/JENKINS-24380)
    Support for new build directory layout in Jenkins 1.597+.

### Version 2.19

No changelog recorded, blame [Unknown User
(kohsuke)](https://wiki.jenkins.io/display/~kohsuke){.confluence-userlink
.user-mention}!

### Version 2.18 (Oct 30 2014)

-   `slow-requests` was missing stack traces.

### Version 2.17 (Oct 10 2014)

-   Capture past slave launch logs, not just the current session
    ([JENKINS-25108](https://issues.jenkins-ci.org/browse/JENKINS-25108))

### Version 2.16 (Oct 8 2014)

-   Prevent slow-request files from going over 2MB in size.

### Version 2.15 (Sept 25 2014)

-   Added some NPE safety against cloud implementations that return
    malformed slave descriptions
-   Slow request content was not properly displaying stack trace
    elements
-   Resolve file descriptor symlinks

### Version 2.14 (Sept 5 2014)

-   Better summarize Item’s of all kinds.
    ([JENKINS-22609](https://issues.jenkins-ci.org/browse/JENKINS-22609))
-   Checking for slow requests threw an exception on an non-locked
    thread.
    ([JENKINS-24567](https://issues.jenkins-ci.org/browse/JENKINS-24567))

### Version 2.13 (Aug 26 2014)

-   Workaround for
    [JENKINS-24358](https://issues.jenkins-ci.org/browse/JENKINS-24358)
    deadlock.
-   Record the query string in slow request logs.
-   Add lock owner thread info to slow request logs.
-   Note the current thread name at each slow request sample.
-   Omit empty `other-logs/*.log`.

### Version 2.12 (Jul 22 2014)

-   Make the SlowRequestChecker timings configurable and only collect
    thread dumps if there are slow requests
    ([JENKINS-23904](https://issues.jenkins-ci.org/browse/JENKINS-23904))

### Version 2.11 (Jul 8 2014)

-   When sending log files remotely, GZip the content to reduce
    bandwidth requirements

### Version 2.10 (Jul 8 2014)

-   The remote node logs were not being transferred once the log file
    grew above a certain undetermined critical size due to GC pressure
    removing an exported remoting reference from the slave's JVM.

### Version 2.9 (Jul 8 2014)

-   When there are many remote nodes, the serial fetching of logs in 2.8
    can cause bundle generation to take an excessive amount of time. Now
    the log fetching is performed in parallel.

### Version 2.8 (Jul 8 2014)

-   Add caching of slave log files with append-based fetch for when the
    cache contains partial content. Should significantly reduce the
    support bundle generation load unless slave logs are generating a
    *lot* of content rapidly.

### Version 2.7 (Jul 4 2014)

-   Make some of the remote operations asynchronous and time bounded in
    order to reduce the impact of periodic support bundle generation and
    speed up bundle generation in general.
-   The only default content that retains synchronous remoting I/O is
    the Log Recorders component.

### Version 2.6 (May 19 2014)

-   Fix file list cap to actually do something, and not throw exceptions
    when hitting the cap.
-   Ignore offline slaves when checking file descriptors (workaround for
    [JENKINS-21999](https://issues.jenkins-ci.org/browse/JENKINS-21999)).

### Version 2.5 (May 12 2014)

-   Reduce memory usage from features added in 2.3.

### Version 2.4 (May 06 2014)

-   New component added in 2.3 usually failed to load.

### Version 2.3 (April 23 2014)

-   Report a thread dump when the jvm starts to become unresponsive.
    Checks every 3 seconds to see if the instance is unresponsive.
-   Report when a deadlock has occurred. Checks every 15 seconds for
    instance of deadlocks occurring.

### Version 2.1 (Mar 24 2014)

-   Robustness for plugins missing descriptors, such as on special slave
    launchers.
-   [JENKINS-22326](https://issues.jenkins-ci.org/browse/JENKINS-22326)
    Deadlock in logging.

### Version 2.0 (Mar 20 2014)

-   Upgraded from Yammer metrics to Codahale/Dropwizard metrics using
    the [Metrics
    Plugin](https://wiki.jenkins.io/display/JENKINS/Metrics+Plugin) (as
    a result, minimum required version of Jenkins has been increased to
    1.520).
-   Fixed bug where large slave launch logs could cause an OOM in
    Jenkins.
-   Reports on LSB modules when defined / available 

### Version 1.8 (Feb 05 2014)

-   Fixes and improvements to thread dump reports.

### Version 1.7 (Feb 04 2014)

-   [JENKINS-20863](https://issues.jenkins-ci.org/browse/JENKINS-20863)
    Fixed integer overflow.
-   [JENKINS-19876](https://issues.jenkins-ci.org/browse/JENKINS-19876)
    Storing custom logs on disk.
-   Added CPU usage information to thread dumps.

### Version 1.6 (Dec 02 2013)

-   Security fixes; please update.
-   [JENKINS-20362](https://issues.jenkins-ci.org/browse/JENKINS-20362)
    Added CLI command.
-   Better plugins/disables.txt.
-   Summarize security configuration.
-   Separate nodes.md.
-   Remember deselected components.
-   Fixed display of impermissible components.

### Version 1.4 (Oct 24th, 2013)

-   Update the User Agent detection resources

### Version 1.3 (Oct 24th, 2013)

-   Ensure AboutUser reports the details of the requesting user and not
    the details of the impersonated user.

### Version 1.2 (Oct 24th, 2013)

-   Simplified threading model when generating bundles

### Version 1.1 (Oct 24th, 2013)

-   Refactoring some of the behaviour of AboutBrowser and AboutUser
    components

### Version 1.0 (Oct 24th, 2013)

-   Initial release
