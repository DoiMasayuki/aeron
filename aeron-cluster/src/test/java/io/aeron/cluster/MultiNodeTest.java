/*
 * Copyright 2014-2020 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.cluster;

import io.aeron.cluster.service.Cluster;
import org.junit.jupiter.api.Test;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class MultiNodeTest
{
    @Test
    public void shouldElectAppointedLeaderWithThreeNodesWithNoReplayNoSnapshot()
    {
        assertTimeoutPreemptively(ofSeconds(10), () ->
        {
            final int appointedLeaderIndex = 1;

            try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(appointedLeaderIndex))
            {
                final TestNode leader = cluster.awaitLeader();

                assertEquals(appointedLeaderIndex, leader.index());
                assertEquals(Cluster.Role.LEADER, leader.role());
                assertEquals(Cluster.Role.FOLLOWER, cluster.node(0).role());
                assertEquals(Cluster.Role.FOLLOWER, cluster.node(2).role());
            }
        });
    }

    @Test
    public void shouldReplayWithAppointedLeaderWithThreeNodesWithNoSnapshot()
    {
        assertTimeoutPreemptively(ofSeconds(10), () ->
        {
            final int appointedLeaderIndex = 1;

            try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(appointedLeaderIndex))
            {
                TestNode leader = cluster.awaitLeader();

                assertEquals(appointedLeaderIndex, leader.index());
                assertEquals(Cluster.Role.LEADER, leader.role());

                cluster.connectClient();
                final int messageCount = 10;
                cluster.sendMessages(messageCount);
                cluster.awaitResponses(messageCount);
                cluster.awaitMessageCountForService(leader, messageCount);

                cluster.stopAllNodes();
                cluster.restartAllNodes(false);

                leader = cluster.awaitLeader();
                cluster.awaitMessageCountForService(leader, messageCount);
                cluster.awaitMessageCountForService(cluster.node(0), messageCount);
                cluster.awaitMessageCountForService(cluster.node(2), messageCount);
            }
        });
    }

    @Test
    public void shouldCatchUpWithAppointedLeaderWithThreeNodesWithNoSnapshot()
    {
        assertTimeoutPreemptively(ofSeconds(10), () ->
        {
            final int appointedLeaderIndex = 1;

            try (TestCluster cluster = TestCluster.startThreeNodeStaticCluster(appointedLeaderIndex))
            {
                TestNode leader = cluster.awaitLeader();

                assertEquals(appointedLeaderIndex, leader.index());
                assertEquals(Cluster.Role.LEADER, leader.role());

                cluster.connectClient();
                final int preCatchupMessageCount = 5;
                final int postCatchupMessageCount = 10;
                final int totalMessageCount = preCatchupMessageCount + postCatchupMessageCount;
                cluster.sendMessages(preCatchupMessageCount);
                cluster.awaitResponses(preCatchupMessageCount);
                cluster.awaitMessageCountForService(leader, preCatchupMessageCount);

                cluster.stopNode(cluster.node(0));

                cluster.sendMessages(postCatchupMessageCount);
                cluster.awaitResponses(postCatchupMessageCount);

                cluster.stopAllNodes();
                cluster.restartAllNodes(false);

                leader = cluster.awaitLeader();
                cluster.awaitMessageCountForService(leader, totalMessageCount);
                cluster.awaitMessageCountForService(cluster.node(0), totalMessageCount);
                cluster.awaitMessageCountForService(cluster.node(2), totalMessageCount);
            }
        });
    }
}
