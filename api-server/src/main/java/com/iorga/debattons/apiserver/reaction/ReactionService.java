package com.iorga.debattons.apiserver.reaction;

import com.iorga.debattons.apiserver.user.UserService;
import com.iorga.debattons.apiserver.util.GraphUtils;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class ReactionService {

  @Autowired
  private GraphUtils graphUtils;

  @Autowired
  private UserService userService;


  public Reaction create(Reaction reaction, String login) throws Exception {
    return graphUtils.doInGraphTransaction(graph -> {
      Vertex reactionVertex = createReactionVertex(reaction, login, graph);

      graphUtils.getRoot(graph).addEdge("created", reactionVertex);
      return reaction;
    });
  }

  private Vertex createReactionVertex(Reaction reaction, String login, Graph graph) throws IOException {
    Vertex userVertex = userService.findUserTraversalByLogin(login, graph).next();

    Vertex reactionVertex = graph.addVertex(
      T.label, "Reaction",
      "title", reaction.getTitle(),
      "content", reaction.getContent(),
      "creationDate", new Date());
    reactionVertex.addEdge("createdBy", userVertex);

    reaction.setId(graphUtils.getStringVertexId(reactionVertex, graph));

    return reactionVertex;
  }

  public Reaction createByReactionReactingToReactionId(Reaction reaction, final String reactToReactionId, String reactionType, String login) throws Exception {
    if (reactToReactionId == null) {
      return create(reaction, login);
    } else {
      return graphUtils.doInGraphTransaction(graph -> {
        Vertex reactionVertex = createReactionVertex(reaction, login, graph);

        Vertex reactedToReactionVertex = graph.traversal().V(graphUtils.getObjectVertexId(reactToReactionId, graph)).has(T.label, "Reaction").next();
        reactionVertex
          .addEdge("reactedTo", reactedToReactionVertex, "reactionType", reactionType != null ? reactionType : "comment");

        return reaction;
      });
    }
  }

  public List<Reaction> findRoots() throws Exception {
    return graphUtils.doInGraphTransaction(graph -> {
      return graphUtils.getRootTraversal(graph)
        .out("created")
        .hasLabel("Reaction")
        .order().by("creationDate", Order.decr)
        .map(vertexTraverser -> {
          try {
            return Reaction.fromVertex(vertexTraverser.get(), graphUtils);
          } catch (IOException e) {
            throw new RuntimeException("Problem while creating the Reaction from Vertex", e);
          }
        }).toList();
    });
  }

  public Reaction findById(final String id) throws Exception {
    return graphUtils.doInGraphTransaction(graph -> {
      return Reaction.fromVertex(
        createReactionTraversalById(id, graph).next(), graphUtils);
    });
  }

  public Reaction findByIdLoadingReactedToDepth(final String id, final int reactedToDepth) throws Exception {
    return graphUtils.doInGraphTransaction(graph -> {
      Vertex reactionVertex = createReactionTraversalById(id, graph).next();
      Reaction reaction = Reaction.fromVertex(reactionVertex, graphUtils);
      loadReactedToByOriginalReactionAndVertexAndDepth(reaction, reactionVertex, reactedToDepth, graph);
      Iterator<Vertex> reactedFromIt = reactionVertex.vertices(Direction.OUT, "reactedTo");
      if (reactedFromIt.hasNext()) {
        reaction.setReactedFrom(Reaction.fromVertex(reactedFromIt.next(), graphUtils));
      }
      return reaction;
    });
  }

  private void loadReactedToByOriginalReactionAndVertexAndDepth(final Reaction originalReaction, Vertex originalVertex, final int depth, Graph graph) throws Exception {
    if (depth > 0) {
      Set<Reaction> reactedTo = new LinkedHashSet<>();
      originalReaction.setReactedTo(reactedTo);

      for (Iterator<Vertex> reactedToVertices = originalVertex.vertices(Direction.IN, "reactedTo"); reactedToVertices.hasNext(); ) {
        Vertex reactedToVertex = reactedToVertices.next();
        Reaction reactedToReaction = Reaction.fromVertex(reactedToVertex, graphUtils);
        reactedTo.add(reactedToReaction);
        if (depth > 1) {
          loadReactedToByOriginalReactionAndVertexAndDepth(reactedToReaction, reactedToVertex, depth - 1, graph);
        }
      }
    }
  }

  private GraphTraversal<Vertex, Vertex> createReactionTraversalById(String id, Graph graph) {
    return graph.traversal().V(graphUtils.getObjectVertexId(id, graph))
      .has(T.label, "Reaction");
  }

  public void agreeWithById(String reactToReactionId, String login) throws Exception {
    graphUtils.doInGraphTransaction(graph -> {
      Vertex user = userService.findUserTraversalByLogin(login, graph).next();
      Vertex reaction = createReactionTraversalById(reactToReactionId, graph).next();
      user.addEdge("agreeWith", reaction);
    });
  }
}
