package cs224n.corefsystems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import cs224n.coref.Document;
import cs224n.coref.Mention;
import cs224n.coref.Sentence;
import cs224n.ling.Tree;
import cs224n.util.Pair;

public class HobbsAlgorithm {
  
  public static Pair<Integer,Integer> parse(Document d, int sentenceNum, Mention m) {
    Sentence s = d.sentences.get(sentenceNum);
    
    int startIndex = m.beginIndexInclusive;
    int endIndex = m.endIndexExclusive;
    
    // 1. Begin at the NP immediately dominating the pronoun
    int parentUID = commonParentIndex(s, startIndex, endIndex-1);
    int dominatingUID = getDominatingNP(s, startIndex, parentUID);
    
    // 2. Go up tree to first NP or S. Call this X, and the path p.
    int xUID = getX(s, startIndex, dominatingUID);
    
    // 3. Traverse all branches below X to the left of p, left-to right, breadth-first.
    //    Propose as antecedent any NP that has a NP or S between it and X
    int minUID = getMinPathUID(s.parse.pathToIndex(startIndex));
    int antecedent = getAntecedent(s.parse, minUID, xUID);
    if (antecedent > -1) {
      return new Pair<Integer,Integer>(antecedent, sentenceNum);
    }
    
    int mainSUID = getHighestSUID(s, startIndex);
    
    while (mainSUID != xUID) {
      // 5. From node X, go up the tree to the first NP or S. Call it X, and the path p.
      xUID = getX(s, startIndex, xUID);
      Tree<String> subtree = returnSubtree(s.parse, xUID);

      // 6. If X is an NP and the path p to X came from a nonhead phrase of X (a specifier
      //    or adjunct, such as a possessive, PP, apposition, or relative clause),
      //    propose X as antecedent (The original said “did not pass through the N’ that
      //    X immediately dominates”, but the Penn Treebank grammar lacks N’ nodes….)
      if (subtree.getLabel().equals("NP")) {
        LinkedList<Pair<String,Integer>> subpath = subtree.pathToIndex(startIndex);
        
        String pathTag = subpath.get(1).getFirst();
        if (!isHead(pathTag)) {
          return new Pair<Integer,Integer>(subpath.get(1).getSecond(), sentenceNum);
        }
      }
      
      // 7. Traverse all branches below X to the left of the path, in a left-to-right,
      //    breadth first manner. Propose any NP encountered as the antecedent
      minUID = getMinPathUID(s.parse.pathToIndex(startIndex));
      antecedent = getAntecedent(s.parse, startIndex, xUID);
      if (antecedent > -1) {
        return new Pair<Integer,Integer>(antecedent, sentenceNum);
      }
      
       // 8. If X is an S node, traverse all branches of X to the right of the path but do
       //    not go below any NP or S encountered. Propose any NP as the antecedent.
      if (subtree.getLabel().equals("S")) {
        List<Tree<String>> bfsSubtree = getBreadthFirstTraversal(subtree);
        List<Integer> exclusions = new ArrayList<Integer>();
        
        for (Tree<String> node : bfsSubtree) {
          String label = node.getLabel();
          int uid = node.getUniqueIndex();
          if (uid < xUID) {
            continue;
          } else if (label.equals("NP")) {
            return new Pair<Integer,Integer>(node.getUniqueIndex(), sentenceNum);
          } else if (label.equals("S") || exclusions.contains(node.getUniqueIndex())) {
            List<Tree<String>> kids = node.getChildren();
            for (Tree<String> kid : kids) {
              exclusions.add(kid.getUniqueIndex());
            }
          } 
        }
        
      }
      
       // 9. Go to step 4
    }
    
    // 4. If X is the highest S in the sentence, traverse the parse trees of the previous
    //    sentences in the order of recency. Traverse each tree left-to-right, breadth
    //    first. When an NP is encountered, propose as antecedent. If X not the highest
    //    node, go to step 5.
    while (sentenceNum > 0) {
      s = d.sentences.get(--sentenceNum);
      int rootUID = s.parse.getUniqueIndex();
      antecedent = getAntecedent(s.parse, Integer.MAX_VALUE, rootUID);
      if (antecedent > -1) {
        return new Pair<Integer,Integer>(antecedent, sentenceNum);
      }
    }
    
    return new Pair<Integer,Integer>(-1, -1);
  }
  
  /**
   * Get the uniqueID of the highest S in the sentence (on the path to the mention)
   */
  public static int getHighestSUID(Sentence s, int startIndex) {
    LinkedList<Pair<String,Integer>> path = s.parse.pathToIndex(startIndex);
    for (Pair<String,Integer> pair : path) {
      if (pair.getFirst().equals("S"))
        return pair.getSecond();
    }
    return -1;
  }
  
  public static int getAntecedent(Tree<String> tree, int minUID, int xUID) {
    Tree<String> subtree = returnSubtree(tree, xUID);
    List<Integer> matchingUIDs = getTagUIDs(subtree, "NP");
    matchingUIDs.addAll(getTagUIDs(subtree, "S"));
    List<Tree<String>> bfsTree = getBreadthFirstTraversal(subtree);
    
    for (Tree<String> node : bfsTree) {
      if (node.getLabel().equals("NP")) {
        int nodeUID = node.getUniqueIndex();
        // Check that an NP exists between this NP and X
        if (hasBetween(matchingUIDs, nodeUID, minUID)) {
          return nodeUID;
        }
      }
    }
    // No match found
    return -1;
  }
  
  
  /**
   * Returns true if the tag is a head tag
   */
  public static boolean isHead(String tag){
    return tag.equals("NN") || tag.equals("NNS") || tag.equals("NNP") || tag.equals("NNPS");
  }
  
  
  /**
   * Return true if the integer list contains a number >min and <max
   */
  public static boolean hasBetween(List<Integer> list, int min, int max) {
    for (int i : list) {
      if (i > min && i < max) {
        return true;
      }
    }
    return false;
  }
  
  
  /**
   * This returns the tree elements in order of BFS
   * @param tree - The root of the tree
   * @return a list of the subtrees in order of breadth-first searching from the root
   */
  public static List<Tree<String>> getBreadthFirstTraversal(Tree<String> tree) {
    ArrayList<Tree<String>>output = new ArrayList<Tree<String>>();
    output.add(tree);
    BFSHelper(output, tree.getChildren());
    return output;
  }
  
  /**
   * Helper function for getBreadthFirstTraversal
   */
  public static void BFSHelper(List<Tree<String>> bfsTree, List<Tree<String>> currentTrees) {
    ArrayList<Tree<String>> childTrees = new ArrayList<Tree<String>>();

    // Add each child to the bfsTree
    for (Tree<String> tree : currentTrees) {
      bfsTree.add(tree);
      childTrees.addAll(tree.getChildren());
    }
    // Recurse if there are more child trees
    if (childTrees.size() > 0)
      BFSHelper(bfsTree, childTrees);
  }
  
  
  /**
   * Return list of UIDs matching a given tag
   * @param tree - Tree
   * @param tag
   * @return
   */
  public static List<Integer> getTagUIDs(Tree<String> tree, String tag) {
    String rootLabel = tree.getLabel();
    List<Integer> matches = new ArrayList<Integer>();
    
    if (rootLabel.equals(tag)) {
      matches.add(tree.getUniqueIndex());
    }
    for (Tree<String> child : tree.getChildren()) {
      List<Integer> result = getTagUIDs(child, tag);
      if (result != null)
        matches.addAll(result);
    }
    return matches;
  }
  
  
  
  /**
   * Get the tree below (and including) a given targetUID
   * @param tree - The superset tree structure containing targetUID
   * @param targetUID - uniqueIndex of the subtree parent
   * @return a (sub)tree
   */
  public static Tree<String> returnSubtree(Tree<String> tree, int targetUID) {
    if (tree.getUniqueIndex() == targetUID) {
      return tree;
    }
    
    for (Tree<String> child : tree.getChildren()) {
      Tree<String> result = returnSubtree(child, targetUID);
      if (result != null)
        return result;
    }
    
    return null;
  }
  
  
  /**
   * Find the uniqueIndex of X
   * @param s - Sentence with a valid .parse
   * @param wordIndex - Position of a word in the mention
   * @param dominatingUID - uniqueIndex of the dominating NP
   * @return the uniqueIndex of the dominating NP
   */
  public static int getX(Sentence s, int wordIndex, int dominatingUID) {
    int xUID = -1;
    LinkedList<Pair<String,Integer>> path = s.parse.pathToIndex(wordIndex);
    
    for (Pair<String,Integer> node : path) {
      String tag = node.getFirst();
      int uid = node.getSecond();
      
      if (uid == dominatingUID)
        break; // We have gone too far down the path
      else if (tag.equals("NP") || tag.equals("S"))
        xUID = uid; // New best guess for X
    }
    
    return xUID;
  }
  
  
  /**
   * Find the uniqueIndex of the dominating Noun-Phrase for the mention
   * @param s - Sentence with a valid .parse
   * @param wordIndex - Position of a word in the mention
   * @param commonParentUID - uniqueIndex of the commonParent of the mention
   * @return the uniqueIndex of the dominating NP
   */
  public static int getDominatingNP(Sentence s, int wordIndex, int commonParentUID) {
    LinkedList<Pair<String,Integer>> path = s.parse.pathToIndex(wordIndex);
    
    int dominatingUID = -1;
    for (Pair<String,Integer> node : path) {
      String tag = node.getFirst();
      int uid = node.getSecond();
      
      if (uid == commonParentUID)        
        break; // We have gone too far down the path
      else if (tag.equals("NP"))
        dominatingUID = uid; // New best guess for the dominating NP
    }
    return dominatingUID;
  }
  
  
  /**
   * Return a set of UIDs from a path
   * @param path - A path output from a Tree<L>
   * @return set of Integer UIDs
   */
  public static Set<Integer> getPathUID(LinkedList<Pair<String,Integer>> path) {
    Set<Integer> uid = new HashSet<Integer>();
    for (Pair<String,Integer> p : path) {
      uid.add(p.getSecond());
    }
    return uid;
  }
  
  /**
   * Return the lowest UID in the path
   * @param path - A path output from a Tree<L>
   * @return set of Integer UIDs
   */
  public static int getMinPathUID(LinkedList<Pair<String,Integer>> path) {
    int min = Integer.MAX_VALUE;
    for (Pair<String,Integer> p : path)
      min = Math.min(min, p.getSecond());
    return min;
  }
  
  
  /**
   * Get the uniqueIndex of the common parent between two words in a sentence
   * @param s - Sentence with a valid .parse
   * @param indexA - Word position of the first word
   * @param indexB - Word position of the second word
   * @return the uniqueIndex of the common parent element in the tree
   */
  public static int commonParentIndex(Sentence s, int indexA, int indexB) {
    LinkedList<Pair<String,Integer>> pathA = s.parse.pathToIndex(indexA);
    LinkedList<Pair<String,Integer>> pathB = s.parse.pathToIndex(indexA);

    int maxDepth = Math.max(pathA.size(), pathB.size());
    int result = -1;
    
    for (int i=0; i < maxDepth; i++) {
      // When they stop matching, we just passed the common parent
      if (pathA.get(i).getSecond() != pathB.get(i).getSecond())
        break;
      else
        result = pathA.get(i).getSecond();
    }
    return result;
  }
  
  
  public static void main(String [] args) {
    // the dog ran home
    String example1 = "(ROOT (S (NP (NNP Stephen) (NNP Moss)) (VP (VBZ hated) (NP (PRP him))) (. .)))";
    //String example = "(ROOT (S (NP (NNP Stephen) (NNP Moss)) (NP (NNP Jack)) (VP (VBZ hated) (NP (PRP him))) (. .)))";
    Tree<String> exampleTree1 = Tree.decode(example1);
    
    System.err.println(exampleTree1);
    
    System.err.println("-- pathToIndex(3) --");
    LinkedList<Pair<String,Integer>> path = exampleTree1.pathToIndex(3);
    for (Pair<String,Integer> p : path) {
      System.err.println(p.getFirst() + "~" + p.getSecond());
    }
    
    System.err.println("-- getTraversalBetween(2,3) --");
    Iterable<Pair<String,Integer>> traversal = exampleTree1.getTraversalBetween(2,3);
    for (Pair<String,Integer> p : traversal) {
      System.err.println(p.getFirst() + "~" + p.getSecond());
    }
    
    System.err.println("-- getPreOrderTraversal() --");
    List<Tree<String>> pot = exampleTree1.getPreOrderTraversal();
    for (int i=0; i < pot.size(); i++) {
      System.err.println("" + i + ": " + pot.get(i).getLabel() + " ~ " + pot.get(i));
    }
    
    System.err.println("-- returnSubtree() --");
    System.err.println(returnSubtree(exampleTree1, 10));
    
    System.err.println("-- getTagUIDs() --");
    System.err.println(getTagUIDs(exampleTree1, "NP"));
    
    //System.err.println("-- getAntecedent() --");
    //System.err.println(getAntecedent(exampleTree, 3, 13));
    //System.err.println(getAntecedent(exampleTree, 4, 17));
    
    System.err.println("-- getBreadthFirstTraversal() --");
    List<Tree<String>> bfs = getBreadthFirstTraversal(exampleTree1);
    for (int i=0; i < bfs.size(); i++) {
      System.err.println("" + i + ": " + bfs.get(i).getLabel() + " ~ " + bfs.get(i));
    }
    
    
    List<Sentence> ss = new ArrayList<Sentence>();
    
    // First Sentence
    String example2 = "(ROOT (S (NP (NNP Niall) (NNP Ferguson)) (VP (VBZ is) (UCP (ADJP (JJ prolific)) (, ,) (ADJP (JJ well-paid)) (CC and) (NP (DT a) (JJ snappy) (NN dresser)))) (. .)))";
    Tree<String> exampleTree2 = Tree.decode(example2);
    List<String> words2 = new ArrayList<String>(Arrays.asList("Niall Ferguson is prolific , well-paid and a snappy dresser .".split(" ")));
    Sentence s2 = new Sentence(words2, words2, words2, words2, words2, exampleTree2);
    
    // Second sentence
    List<String> words1 = new ArrayList<String>(Arrays.asList("Stephen Moss hated him .".split(" ")));
    Sentence s1 = new Sentence(words1, words1, words1, words1, words1, exampleTree1);
    
    ss.add(s2);
    ss.add(s1);
    
    Document d = new Document("test", ss);
    //Pair<Integer,Integer> out = parse(d, 1);
    //System.err.println("-- parse() --");
    //System.err.println(out);
    
  }
  
}
