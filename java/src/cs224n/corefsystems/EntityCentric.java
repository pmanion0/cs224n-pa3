package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import cs224n.coref.*;
import cs224n.ling.Tree;
import cs224n.util.CounterMap;
import cs224n.util.Pair;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;


import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.classifiers.functions.Logistic;

public class EntityCentric implements CoreferenceSystem {
  
  public CounterMap<String,String> synonyms = new CounterMap<String,String>();
   // Create word sets that we will treat differently and/or exclude from certain rules
  public static final String[] LINKING_VERBS = new String[] {"am","is","are","was","were","being"};
  public static final Set<String> linkingVerbList = new HashSet<String>(Arrays.asList(LINKING_VERBS));
  public static final String[] STOP_WORDS = new String[] {"a","an","and","are","as","at","be","by","for",
      "from","has","in","is","it","its","of","on","that","the","to","was","were","will","with"};
  public static Set<String> stopWordList = new HashSet<String>(Arrays.asList(STOP_WORDS));
  
  public Classifier model;
  Instances dataset = null;


  @Override
  public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
      Instances dataset = extract_train_features(trainingData);
      modeling(dataset);
  }
  
    public Instances extract_train_features(Collection<Pair<Document, List<Entity>>> trainingData) {

      /* Build attributes list */
      Attribute Attribute1 = new Attribute("firstNumeric");
      Attribute Attribute2 = new Attribute("secondNumberic");
      
      FastVector fvClassVal = new FastVector(2);
      fvClassVal.addElement("yes");
      fvClassVal.addElement("no");
      Attribute ClassAttribute = new Attribute("theClass", fvClassVal);

      FastVector attributes = new FastVector(3);
      attributes.addElement(Attribute1);   
      attributes.addElement(Attribute2);
      attributes.addElement(ClassAttribute);
          
    dataset = new Instances("train_dataset", attributes, 0);
		
    for(Pair<Document, List<Entity>> pair : trainingData){
      Document doc = pair.getFirst();
      List<Entity> clusters = pair.getSecond();
         
       //--Iterate Over Coreferent Mention Pairs
       int distance;
       int lemmaMatch;
       for(Entity e : clusters){
         for(Pair<Mention, Mention> mentionPair : e.orderedMentionPairs()){
           Mention m1 = mentionPair.getFirst();
           Mention m2 = mentionPair.getSecond();
           distance = java.lang.Math.abs(m1.doc.indexOfMention(m1) - m2.doc.indexOfMention(m2));
           lemmaMatch = (m1.headToken().lemma().equals(m2.headToken().lemma())) ? 0 : 1;
           //add data 
           Instance row = new Instance(3);
           row.setValue((Attribute)attributes.elementAt(0), distance);      
           row.setValue((Attribute)attributes.elementAt(1), lemmaMatch);    
           row.setValue((Attribute)attributes.elementAt(2), "yes");    
           dataset.add(row);
         
         }
       }
       
       for (Entity e : clusters){ 
         for (Mention m1 : e.mentions)
          for (Entity e2: clusters){
            if (!e2.equals(e)){
              for (Mention m2: e2.mentions){
                distance = java.lang.Math.abs(m1.doc.indexOfMention(m1) - m2.doc.indexOfMention(m2));
                lemmaMatch = (m1.headToken().lemma().equals(m2.headToken().lemma())) ? 0 : 1;
                //add data 
                Instance row = new Instance(3);
                row.setValue((Attribute)attributes.elementAt(0), distance);      
                row.setValue((Attribute)attributes.elementAt(1), lemmaMatch);    
                row.setValue((Attribute)attributes.elementAt(2), "no");    
                dataset.add(row);
               }
            }
           }
         }
         
       } 
		/* Set last attribute as target */
                dataset.setClassIndex(dataset.numAttributes()-1);
		return dataset;
     }

   public void modeling(Instances dataset){ 
          model = (Classifier)new Logistic();   
         try {
          model.buildClassifier(dataset);
           // Test the model
          Evaluation eTest = new Evaluation(dataset);
          eTest.evaluateModel(model, dataset);

          // Print the result à la Weka explorer:
          String strSummary = eTest.toSummaryString();
          System.out.println(strSummary); 
          
          double[][] cmMatrix = eTest.confusionMatrix();
          for(int row_i=0; row_i<cmMatrix.length; row_i++){
             for(int col_i=0; col_i<cmMatrix.length; col_i++){
                 System.out.print(cmMatrix[row_i][col_i]);
                 System.out.print("|");
             }
             System.out.println();
            }
             for (int i = 0; i< 10; i++){
            double dist[] = model.distributionForInstance(dataset.instance(i));
            System.out.println("instance_i = " + dataset.instance(i));
            System.out.println("prediction  = " + dist[0]);
             }
          }
        catch(Exception e) {
     // If it fails, write the error message to screen
           e.printStackTrace(); 
         } 
         
   }   
      
  @Override
  public List<ClusteredMention> runCoreference(Document doc) {
    List<ClusteredMention> output;
    
    output = allSingleton(doc);
    
    exactMatch(output);
    acronymMatch(output);
    noStopWordMatch(output);
    dropAfterHeadMatch(output);
    //appositiveMatch(output);
    headExactMatch(output);
    headLowcaseMatch(output);
    headLemmaMatch(output);
    //partialOverlapMatch(output);
    //headLooseMatch(output);
    //appositiveMatch(output);
    //predicateNominativeMatch(output);
    //hobbsMatch(output, doc);
    pronounMatch(output);
    
    return output;
  }

  public double scoring(Mention m1, Mention m2){
         try{
           
          Instance inst = new Instance(3);
          int  distance = java.lang.Math.abs(m1.doc.indexOfMention(m1) - m2.doc.indexOfMention(m2));
           int lemmaMatch = (m1.headToken().lemma().equals(m2.headToken().lemma())) ? 0 : 1;
          inst.setValue(dataset.attribute(0),distance);
          inst.setValue(dataset.attribute(1),lemmaMatch);
          inst.setValue(dataset.attribute(2), "yes");
          inst.setMissing(inst.numAttributes()-1);  
            //System.out.println("distance = " + distance);
            double prob[] = model.distributionForInstance(inst);
            return prob[0];
          }
          catch(Exception e) {
     // If it fails, write the error message to screen
           e.printStackTrace(); 
           return 99.0;
        } 
   } 
  /**
   * Return TRUE if either mention is a pronoun
   */
  public boolean eitherIsPronoun(ClusteredMention cm1, ClusteredMention cm2) {
    return Pronoun.isSomePronoun(cm1.mention.gloss()) || Pronoun.isSomePronoun(cm2.mention.gloss());
  }
  
  /**
   * Convert all mentions into singleton ClusteredMentions
   * @param doc - Document set with all Mentions
   * @return list of all ClusteredMentions
   */
  public List<ClusteredMention> allSingleton(Document doc) {
    List<ClusteredMention> output = new ArrayList<ClusteredMention>();
    for (Mention m : doc.getMentions()) {
      ClusteredMention newCluster = m.markSingleton();
      output.add(newCluster);
    }
    return output;
  }
  
  /**
   * Merge clusters of any mentions with exact matches (excluding pronouns)
   * @param currentClusters - list of all ClusteredMentions
   */
  public void exactMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      for (int j=i; j < currentClusters.size(); j++) {
        ClusteredMention cm2 = currentClusters.get(j);
        
        if (!eitherIsPronoun(cm1, cm2) && cm1.mention.gloss().equals(cm2.mention.gloss())) {
          mergeClusters(cm1.entity, cm2.entity);
        }
        
        //test scoring
        double pred = scoring(cm1.mention,cm2.mention);
        //System.out.println(pred);
       }
    }
  }
  
  /**
   * Merge clusters if the head word matches exactly
   * @param currentClusters - list of all ClusteredMentions
   */
  public void headExactMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      if (mentionSelection(cm1)){
        for (int j=i; j < currentClusters.size(); j++) {
          ClusteredMention cm2 = currentClusters.get(j);

          if (!eitherIsPronoun(cm1, cm2) && cm1.mention.headWord().equals(cm2.mention.headWord())) {
            mergeClusters(cm1.entity, cm2.entity);
          }
        }
      }
    }
  }
  
  /**
   * Merge clusters if the head word matches exactly
   * @param currentClusters - list of all ClusteredMentions
   */
  public void headLowcaseMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      if (mentionSelection(cm1)){
        for (int j=i; j < currentClusters.size(); j++) {
          ClusteredMention cm2 = currentClusters.get(j);

          if (!eitherIsPronoun(cm1, cm2)
              && cm1.mention.headWord().toLowerCase().equals(cm2.mention.headWord().toLowerCase())) {
            mergeClusters(cm1.entity, cm2.entity);
          }
        }
      }
    }
  }
  
  /**
   * Merge clusters if the head lemma matches
   * @param currentClusters - list of all ClusteredMentions
   */
  public void headLemmaMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      for (int j=i; j < currentClusters.size(); j++) {
        ClusteredMention cm2 = currentClusters.get(j);
        
        if (!eitherIsPronoun(cm1, cm2)
            && cm1.mention.headToken().lemma().equals(cm2.mention.headToken().lemma())) {
          mergeClusters(cm1.entity, cm2.entity);
        }
      /*  if (cm1.mention.headToken().lemma().equals(cm2.mention.headToken().lemma())&containUppercase(cm1.mention.gloss())&&containUppercase(cm2.mention.gloss())&&!cm1.mention.gloss().equals(cm2.mention.gloss())){
          System.out.println(cm1.mention.sentence.lemmas);
          System.out.println(cm1.mention.sentence);
          System.out.println(cm1.mention.gloss());
          System.out.println(cm2.mention.sentence.lemmas);
          System.out.println(cm2.mention.sentence);
          System.out.println(cm2.mention.gloss());
       
        }
      */
          
      }
    }
  }
  
  public boolean containUppercase(String a ) {

                     for (String word : a.split(" ")){

                      String firstLetter = String.valueOf(word.charAt(0)); 
                      if (firstLetter.equals(firstLetter.toUpperCase()))
                        return true;
                    }
                    return false;

                  }
  /**
   * Merge clusters if the head words have an NER match
   * @param currentClusters - list of all ClusteredMentions
   */
  public void headLooseMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      for (int j=i; j < currentClusters.size(); j++) {
        ClusteredMention cm2 = currentClusters.get(j);
        
        if (!eitherIsPronoun(cm1, cm2) && cm1.mention.gloss().contains(cm1.mention.headWord())
            && cm1.mention.headToken().nerTag().equals(cm2.mention.headToken().nerTag())) {
          mergeClusters(cm1.entity, cm2.entity);
        }
      }
    }
  }
public boolean mentionSelection(ClusteredMention cm){
    int loc = cm.mention.doc.indexOfMention(cm.mention);
    for (Mention m : cm.entity.mentions){
      if (loc > m.doc.indexOfMention(m))
        return false;
    }
    return true;
  }

  /**
   * Merge clusters if one is an acronym of the other
   * @param currentClusters - list of all ClusteredMentions
   */
  public void acronymMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      if (mentionSelection(cm1)){
        for (int j=0; j < currentClusters.size(); j++) {
          ClusteredMention cm2 = currentClusters.get(j);
          //if (mentionSelection(cm2)){
            String acronym = acronym(cm1.mention.gloss());
            String base = cm2.mention.gloss();
            if (!eitherIsPronoun(cm1, cm2) && base.indexOf(acronym)>-1 && acronym.length()>1) {
              mergeClusters(cm1.entity, cm2.entity);
            }
          //}
        }
      }
    }
  }
  
  /**
   * Merges clusters if any two mentions are separate by just a comma
   * @param currentClusters - list of all ClusteredMentions
   */
  public void appositiveMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      for (int j=i+1; j < currentClusters.size(); j++) {
        ClusteredMention cm2 = currentClusters.get(j);
        
        // Merge if they occur in the same sentence and are separate by a comma
        if (cm1.mention.sentence.gloss().equals(cm2.mention.sentence.gloss())
            && (cm1.mention.endIndexExclusive+1) == cm2.mention.beginIndexInclusive
            && cm2.mention.sentence.length() > cm2.mention.endIndexExclusive
            && !cm1.mention.headToken().nerTag().equals("DATE") // Often dates are used in this way
            && !cm1.mention.headToken().nerTag().equals("GPE") // Often have place names used this way too
            && cm1.mention.headToken().nerTag().equals(cm2.mention.headToken().nerTag()) // Things like "Patrick, ABC News, signing out"
            && cm2.mention.sentence.words.get(cm2.mention.endIndexExclusive).equals(",")
            && cm1.mention.sentence.words.get(cm1.mention.endIndexExclusive).equals(",")) {
              System.err.println("APPOSITIVE MATCH");
              mergeClusters(cm1.entity, cm2.entity);
        }
      }
    }
  }
  
  /**
   * Merges clusters if any two mentions are separate by just a comma
   * @param currentClusters - list of all ClusteredMentions
   */
  public void predicateNominativeMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      for (int j=i+1; j < currentClusters.size(); j++) {
        ClusteredMention cm2 = currentClusters.get(j);
        
        // Merge if they occur in the same sentence and are separate by a comma
        if (cm1.mention.sentence.gloss().equals(cm2.mention.sentence.gloss())
            && (cm1.mention.endIndexExclusive+1) == cm2.mention.beginIndexInclusive
            && linkingVerbList.contains(cm1.mention.sentence.words.get(cm1.mention.endIndexExclusive).toLowerCase())
            && cm1.mention.headToken().nerTag().equals(cm2.mention.headToken().nerTag())) {
              mergeClusters(cm1.entity, cm2.entity);
        }
      }
    }
  }
  
  /**
   * Merges clusters if all the words (minus stop words) are the same
   * @param currentClusters - list of all ClusteredMentions
   */
  public void noStopWordMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      if (mentionSelection(cm1)){

        for (int j=i+1; j < currentClusters.size(); j++) {
          ClusteredMention cm2 = currentClusters.get(j);
          //if (mentionSelection(cm2)){
            if (!eitherIsPronoun(cm1, cm2)
                && matchWithoutStopWords(cm1.mention.text(), cm2.mention.text()))
                  mergeClusters(cm1.entity, cm2.entity);
          //}
        }
      }
    }
  }
  /**
   * Return TRUE if the two word lists match after excluding stop words
   */
  public boolean matchWithoutStopWords(List<String> a, List<String> b) {
    // Make sure all non-stop words in A also occur in B
    for (String word : a) {
      if (!stopWordList.contains(word.toLowerCase()) && !b.contains(word))
        return false;
    }
    // Make sure all non-stop words in B also occur in A
    for (String word : b) {
      if (!stopWordList.contains(word.toLowerCase()) && !a.contains(word))
        return false;
    }
    return true;
  }
  
  
  public void dropAfterHeadMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      if (mentionSelection(cm1)){
        for (int j=i+1; j < currentClusters.size(); j++) {
          ClusteredMention cm2 = currentClusters.get(j);
          String s1 = "", s2 = "";
          for (int k=cm1.mention.beginIndexInclusive; k <= cm1.mention.headWordIndex; k++)
            s1 += cm1.mention.sentence.words.get(k);
          for (int k=cm2.mention.beginIndexInclusive; k <= cm2.mention.headWordIndex; k++)
            s2 += cm2.mention.sentence.words.get(k);
          if (!eitherIsPronoun(cm1, cm2) && !s1.equals("") && s1.equals(s2))
              //&& cm1.mention.headToken().nerTag().equals(cm2.mention.headToken().nerTag())
              //&& (cm1.mention.headToken().isNoun() || cm1.mention.headToken().isProperNoun())
              //&& (cm2.mention.headToken().isNoun() || cm2.mention.headToken().isProperNoun()))
            mergeClusters(cm1.entity, cm2.entity);
        }
      }
    }
  }

  /**
   * Match mentions that overlap a certain fraction of their words
   */
  public void partialOverlapMatch(List<ClusteredMention> currentClusters) {
    for (int i=0; i < currentClusters.size(); i++) {
      ClusteredMention cm1 = currentClusters.get(i);
      for (int j=i+1; j < currentClusters.size(); j++) {
        ClusteredMention cm2 = currentClusters.get(j);
        
        double length = 0, overlap = 0;
        List<String> s_long, s_short;
        if (cm1.mention.length() > cm2.mention.length()) {
          length = cm1.mention.length();
          s_long = Arrays.asList(cm1.mention.gloss().split(" "));
          s_short = Arrays.asList(cm2.mention.gloss().split(" "));
        } else {
          length = cm2.mention.length();
          s_long = Arrays.asList(cm2.mention.gloss().split(" "));
          s_short = Arrays.asList(cm1.mention.gloss().split(" "));
        }
        
        for (String s : s_short) {
          if (s_long.contains(s))
            overlap++;
        }
        if (overlap/length > 0.66 && length > 3)
          mergeClusters(cm1.entity, cm2.entity);
      }
    }
  }
  
  public void pronounMatch(List<ClusteredMention> currentClusters) {
    // Separate pronouns from non-pronouns
    List<ClusteredMention> proList = new ArrayList<ClusteredMention>();
    List<ClusteredMention> nonList = new ArrayList<ClusteredMention>();
    for (ClusteredMention cm : currentClusters) {
      if (mentionSelection(cm)){
        if (Pronoun.isSomePronoun(cm.mention.headWord())) {
          proList.add(cm);
        } else {
          nonList.add(cm);
        }
      }
    }
    
    // Assign every pronoun to the best match
    for (ClusteredMention pro : proList) {
      Entity bestMatch = null;
      int bestDistance = Integer.MAX_VALUE;
      
      for (ClusteredMention non : nonList) {
        /*Gender pronoun, head;
        if (Pronoun.isSomePronoun(cm1.mention.headWord())) {
          pronoun = Pronoun.getPronoun(cm1.mention.headWord()).gender;
          head = Name.mostLikelyGender(cm2.mention.headWord());
        } else {
          pronoun = Pronoun.getPronoun(cm2.mention.headWord()).gender;
          head = Name.mostLikelyGender(cm1.mention.headWord());
        }*/
        
        int distance = pro.mention.doc.indexOfMention(pro.mention) - non.mention.doc.indexOfMention(non.mention);
        if (distance < bestDistance && distance > 0
            && isNerMatch(pro.mention, non.mention)
            && isGenderMatch(pro.mention, non.mention)
            && isNumberMatch(pro.mention, non.mention)
            //&& isPersonMatch(pro.mention, non.mention)
            ) {
          bestDistance = distance;
          bestMatch = non.entity;
        /*int distance = pro.mention.doc.indexOfMention(pro.mention) - non.mention.doc.indexOfMention(non.mention);
        if (isPronounMatch(pro.mention, non.mention) && distance < bestDistance && distance > 0) {
          bestDistance = distance;
          bestMatch = non.entity;*/
        }
      }
      if (bestMatch != null)
        mergeClusters(pro.entity, bestMatch);
    }
  }
  
  
  
  
  
  
  
  
  
  
  
  

  /**
   * Convert a string into its acronym
   */
  public String acronym(String in) {
    String out = "";
    String[] split = in.split(" ");
    for (String word : split) {
      char firstChar = word.charAt(0);
      if (Character.isLetter(firstChar) && Character.isUpperCase(firstChar))
        out += firstChar;
    }
    return out;
  }
  
  
  
  
  
  
  
  
  
  
  
  
  
  public void hobbsMatch(List<ClusteredMention> currentClusters, Document doc) {
    for (ClusteredMention cm : currentClusters) {
      // Skip non-pronouns or mentions that have already been clustered
      if (!Pronoun.isSomePronoun(cm.mention.gloss()) || cm.entity.size() > 1)
        continue;
      /* DEBUG System.err.println("--- PRONOUN FOUND ---"); */
      Entity e = getHobbsParse(cm.mention, doc, currentClusters);
      if (e != null)
        cm.mention.changeCoreference(e);
    }
  }
  
  
  /**
   * Get the matching entity for a Hobbs Parse
   * @param m - Mention with the pronoun to match
   * @param d - Document set for parsing
   * @param clusters - Cluster list to use in finding the matching entity
   * @return Entity of the matching mention
   */
  public Entity getHobbsParse(Mention m, Document d, List<ClusteredMention> clusters) {
    Entity matchingEntity = null;
    
    Pair<Integer,Integer> match = HobbsAlgorithm.parse(d, m);
    int matchUID = match.getFirst();
    int matchSentenceNum = match.getSecond();
    
    if (matchUID > -1 && matchSentenceNum > -1) {
      Sentence matchSentence = d.sentences.get(matchSentenceNum);
      Tree<String> matchParse = HobbsAlgorithm.returnSubtree(matchSentence.parse, matchUID);
      
      for (ClusteredMention c : clusters) {
        if (matchSentence.equals(c.mention.sentence) && matchParse.equals(c.mention.parse)) {
          matchingEntity = c.entity;
        }
      }
    }
    
    return matchingEntity;
  }
  
  
  /**
   * Merge all mentions from e1 to e2
   */
  public static void mergeClusters(Entity e1, Entity e2) {
    List<Mention> m1List = new ArrayList<Mention>();
    for (Mention m1 : e1.mentions) {
      m1List.add(m1);
    }
    for (Mention m1 : m1List) {
      m1.changeCoreference(e2);
    }
  }
  
  /**
   * Convert Hobbs output to a mention
   */
  public static ClusteredMention hobbsToMention(List<ClusteredMention> clusters, Sentence s, List<String> words) {
    for (ClusteredMention cm : clusters) {
      Mention m = cm.mention;
      if (m.sentence == s && stringListMatch(m.text(), words))
        return cm;
    }
    return null;
  }
  
  
  /**
   * Determine if the two lists of strings match
   */
  public static boolean stringListMatch(List<String> a, List<String> b) {
    if (a.size() != b.size())
      return false;
    for (int i=0; i < a.size(); i++) {
      if (!a.get(i).equals(b.get(i))) {
        return false;
      }
    }
    return true;
  }
  

  
  
  /**
   * Returns TRUE if Word Inclusion is satisfied, otherwise FALSE, not finished - need to remove stop words
   */
  public boolean isWordInclusion(Entity entity, Mention newMention){
    String clusterString = new String();
      for (Mention m : entity.mentions)
        clusterString += " " + m.gloss().toLowerCase();
      
    for (String word : newMention.gloss().toLowerCase().split(" ")){
      if (!stopWordList.contains(word) && !clusterString.contains(word))
        return false;
    }
    return true;
  }
  
  /**
   * Returns TRUE if two mentions have Compatible Modifiers is satisfied, otherwise FALSE
   */
  public boolean isCompatibleModifiers(Mention mention, Mention newMention){
    String newText = newMention.gloss();
    List<String> newPosTags = newMention.sentence.posTags;
    int index = newMention.beginIndexInclusive;
    String posTag;
    for (String word : newText.split(" ")){
      posTag = newPosTags.get(index);
      if (posTag.equals("NN") || posTag.equals("JJ")|| posTag.equals("JJS") || posTag.equals("JJR")){
        if (!mention.gloss().contains(word))
          return false;
      index++;
      }
    }
    return false;
  }
  
  /**
   * Returns TRUE if a newMention satisfy Cluster Head Match with an entity, otherwise FALSE
   */
  public boolean isRelaxedClusterHeadMatch(Entity entity, Mention newMention){
    for (Mention m : entity.mentions){
      if (m.gloss().toLowerCase().contains(newMention.headWord().toLowerCase()))
              return true;
    }
    return false;
  }
  
  /**
   * Returns TRUE if two mentions are labeled as the named entities and the types match, otherwise FALSE
   */
  public boolean isNerMatch(Mention mention, Mention newMention){
    String nerTag = mention.sentence.nerTags.get(mention.beginIndexInclusive);
    String newNerTag = newMention.sentence.nerTags.get(newMention.beginIndexInclusive);
    if (nerTag!= "0" && nerTag.equals(newNerTag))
      return true;
    else
      return false;
  }
  
   /**
   * Returns TRUE if two mentions have gender match, otherwise FALSE
   */
  public boolean isGenderMatch(Mention m1, Mention m2){
    Pair<Boolean,Boolean> gender = Util.haveGenderAndAreSameGender(m1, m2);
    return(gender.getFirst() && gender.getSecond())|| !gender.getFirst();
  }
  
  /**
   * Returns TRUE if two mentions have number match, otherwise FALSE
   */
  public boolean isNumberMatch(Mention m1, Mention m2){
    Pair<Boolean,Boolean> number = Util.haveNumberAndAreSameNumber(m1, m2);
    return (number.getFirst() && number.getSecond()) || !number.getFirst();
  }
  
  /**
   * Returns TRUE if two mentions have person match, otherwise FALSE
   */
  public boolean isPersonMatch(Mention m1, Mention m2){
    boolean flag = true;
    if (Pronoun.isSomePronoun(m1.gloss()) && Pronoun.isSomePronoun(m2.gloss())) {
      if (!(m1.headToken().isQuoted() || m2.headToken().isQuoted())) {
        Pronoun p1 = Pronoun.valueOrNull(m1.gloss());
        Pronoun p2 = Pronoun.valueOrNull(m2.gloss());
        if (p1 != null && p2 != null) {
          flag = (p1.speaker == p2.speaker);
        }
      }
    }
    return flag;
  }
  
  boolean isPronounMatch(Mention m1, Mention m2) {
     
    boolean nerMatch = m1.headToken().nerTag().equals(m2.headToken().nerTag());
    boolean lemmasMatch = true; // m1.headToken().lemma().equals(m2.headToken().lemma());

    return isGenderMatch(m1,m2) && isNumberMatch(m1,m2) && isPersonMatch(m1,m2) && nerMatch && lemmasMatch;
  }

  
  
  
  
  
  /** 
   * check if two string contain exactly the same set of words which may not be in the same order
   */
  public boolean containsUnorderredWords(String a, String b) {
    boolean flag = true;
    for (String word : a.split(" ")){
      if (!b.toLowerCase().contains(word.toLowerCase()))
        flag = false;
    }    
    return flag;
  }
  
  
  
  /**
   * Get all entities present in the ClusteredMention list
   */
  public Set<Entity> getEntities(List<ClusteredMention> clusters) {
    Set<Entity> entities = new HashSet<Entity>();
    for (ClusteredMention cm: clusters) {
      entities.add(cm.entity);
    }
    return entities;
  }
}