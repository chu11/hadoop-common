To get this work I also had to do when running Hadoop

in hadoop-env.sh

export HADOOP_CLASSPATH=$HADOOP_CLASSPATH:/home/achu/hadoop/hadoop-github-trunk/mlx-3.x/uda-hadoop-3.x.jar:/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/gov/llnl/lc/chaos/Munge.jar

# Extra Java runtime options.  Empty by default.
export HADOOP_OPTS="-Djava.net.preferIPv4Stack=true $HADOOP_CLIENT_OPTS -Djava.library.path=/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs"

# Command specific options appended to HADOOP_OPTS when specified
export HADOOP_NAMENODE_OPTS="-Dhadoop.security.logger=${HADOOP_SECURITY_LOGGER:-INFO,RFAS} -Dhdfs.audit.logger=${HDFS_AUDIT_LOGGER:-INFO,NullAppender} $HADOOP_NAMENODE_OPTS -Djava.library.path=/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs"
export HADOOP_DATANODE_OPTS="-Dhadoop.security.logger=ERROR,RFAS $HADOOP_DATANODE_OPTS -Djava.library.path=/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs"

export HADOOP_SECONDARYNAMENODE_OPTS="-Dhadoop.security.logger=${HADOOP_SECURITY_LOGGER:-INFO,RFAS} -Dhdfs.audit.logger=${HDFS_AUDIT_LOGGER:-INFO,NullAppender} $HADOOP_SECONDARYNAMENODE_OPTS -Djava.library.path=/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs"

in yarn-env.sh

if [ "x$JAVA_LIBRARY_PATH" != "x" ]; then
  YARN_OPTS="$YARN_OPTS -Djava.library.path=$JAVA_LIBRARY_PATH;/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs"
else
  YARN_OPTS="$YARN_OPTS -Djava.library.path=/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs"
fi  

in yarn-site.xml

  <property>
    <description>CLASSPATH for YARN applications. A comma-separated list
    of CLASSPATH entries</description>
     <name>yarn.application.classpath</name>
     <value>$HADOOP_CONF_DIR,$HADOOP_COMMON_HOME/share/hadoop/common/*,$HADOOP_COMMON_HOME/share/hadoop/common/lib/*,$HADOOP_HDFS_HOME/share/hadoop/hdfs/*,$HADOOP_HDFS_HOME/share/hadoop/hdfs/lib/*,$HADOOP_YARN_HOME/share/hadoop/yarn/*,$HADOOP_YARN_HOME/share/hadoop/yarn/lib/*,/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/gov/llnl/lc/chaos/Munge.jar</value>
  </property>

in mapred-site.xml

<property>
  <name>mapred.child.env</name>
  <value>LD_LIBRARY_PATH=/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs</value>
  <description>User added environment variables for the task tracker child
  processes. Example :
  1) A=foo  This will set the env variable A to foo
  2) B=$B:c This is inherit nodemanager's B env variable.
  </description>
</property>

<property>
  <name>mapreduce.admin.user.env</name>
  <value>LD_LIBRARY_PATH=$HADOOP_COMMON_HOME/lib/native:/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs</value>
  <description>Expert: Additional execution environment entries for
  map and reduce task processes. This is not an additive property.
  You must preserve the original value if you want your map and
  reduce tasks to have access to native libraries (compression, etc).
  </description>
</property>


<property>
  <name>mapred.map.env</name>
  <value>LD_LIBRARY_PATH=/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs</value>
  <description>User added environment variables for the task tracker child
  processes. Example :
  1) A=foo  This will set the env variable A to foo
  2) B=$B:c This is inherit nodemanager's B env variable.
  </description>
</property>

<property>
  <name>mapred.reduce.env</name>
  <value>LD_LIBRARY_PATH=/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs</value>
  <description>User added environment variables for the task tracker child
  processes. Example :
  1) A=foo  This will set the env variable A to foo
  2) B=$B:c This is inherit nodemanager's B env variable.
  </description>
</property>

<property>
  <name>yarn.app.mapreduce.am.env</name>
  <value>LD_LIBRARY_PATH=/home/achu/hadoop/hadoop-github-trunk-munge/mungejni/src/.libs</value>
  <description>User added environment variables for the MR App Master
  processes. Example :
  1) A=foo  This will set the env variable A to foo
  2) B=$B:c This is inherit tasktracker's B env variable.
  </description>
</property>

