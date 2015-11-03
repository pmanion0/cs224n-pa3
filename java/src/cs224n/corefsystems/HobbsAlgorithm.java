package cs224n.corefsystems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import cs224n.coref.Sentence;
import cs224n.ling.Tree;
import cs224n.util.Pair;
import edu.stanford.nlp.dcoref.Mention;

public class HobbsAlgorithm {
  
  public static void parse(Sentence s, Mention m) {
    
    int startIndex = m.startIndex;
    int endIndex = m.endIndex;
    
    // 1. Begin at the NP immediately dominating the pronoun
    int parentUID = commonParentIndex(s, startIndex, endIndex);
    int dominatingUID = getDominatingNP(s, startIndex, parentUID);
    
    // 2. Go up tree to first NP or S. Call this X, and the path p.
    int xUID = getX(s, startIndex, dominatingUID);
    
    // 3. Traverse all branches below X to the left of p, left-to right, breadth-first.
    //    Propose as antecedent any NP that has a NP or S between it and X
    int antecedent = getAntecedent(s.parse, startIndex, xUID);
    if (antecedent > -1) {
      return; // result is antecedent
    }
    
    // 4. If X is the highest S in the sentence, traverse the parse trees of the previous
    //    sentences in the order of recency. Traverse each tree left-to-right, breadth
    //    first. When an NP is encountered, propose as antecedent. If X not the highest
    //    node, go to step 5.
      
    // 5. From node X, go up the tree to the first NP or S. Call it X, and the path p.
      
    // 6. If X is an NP and the path p to X came from a nonhead phrase of X (a specifier
    //    or adjunct, such as a possessive, PP, apposition, or relative clause),
    //    propose X as antecedent (The original said “did not pass through the N’ that
    //    X immediately dominates”, but the Penn Treebank grammar lacks N’ nodes….)
     
    // 7. Traverse all branches below X to the left of the path, in a left-to-right,
    //    breadth first manner. Propose any NP encountered as the antecedent
     
     // 8. If X is an S node, traverse all branches of X to the right of the path but do
     //    not go below any NP or S encountered. Propose any NP as the antecedent.
     
     // 9. Go to step 4
   
  }
  
  
  public static int getAntecedent(Tree<String> tree, int startIndex, int xUID) {
    int minUID = getMinPathUID(tree.pathToIndex(startIndex));
    Tree<String> subtree = returnSubtree(tree, xUID);
    List<Integer> matchingUIDs = getTagUIDs(subtree, "NP");
    
    int validCount = 0;
    
    for (int uid : matchingUIDs) {
      if (uid < minUID)
        validCount++;
    }
    
    if (validCount > 1)
      return matchingUIDs.get(0);
    else
      return -1;
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
    //String example = "(ROOT (S (NP (NNP Stephen) (NNP Moss)) (VP (VBZ hated) (NP (PRP him))) (. .)))";
    String example = "(ROOT (S (NP (NNP Stephen) (NNP Moss)) (NP (NNP Jack)) (VP (VBZ hated) (NP (PRP him))) (. .)))";
    Tree<String> exampleTree = Tree.decode(example);
    
    System.err.println(exampleTree);
    
    System.err.println("-- pathToIndex(3) --");
    LinkedList<Pair<String,Integer>> path = exampleTree.pathToIndex(3);
    for (Pair<String,Integer> p : path) {
      System.err.println(p.getFirst() + "~" + p.getSecond());
    }
    
    System.err.println("-- getTraversalBetween(2,3) --");
    Iterable<Pair<String,Integer>> traversal = exampleTree.getTraversalBetween(2,3);
    for (Pair<String,Integer> p : traversal) {
      System.err.println(p.getFirst() + "~" + p.getSecond());
    }
    
    System.err.println("-- getPreOrderTraversal() --");
    List<Tree<String>> pot = exampleTree.getPreOrderTraversal();
    for (int i=0; i < pot.size(); i++) {
      System.err.println("" + i + ": " + pot.get(i).getLabel() + " ~ " + pot.get(i));
    }
    
    System.err.println("-- returnSubtree() --");
    System.err.println(returnSubtree(exampleTree, 10));
    
    System.err.println("-- getTagUIDs() --");
    System.err.println(getTagUIDs(exampleTree, "NP"));
    
    System.err.println("-- getAntecedent() --");
    //System.err.println(getAntecedent(exampleTree, 3, 13));
    System.err.println(getAntecedent(exampleTree, 4, 17));
    
  }

}
