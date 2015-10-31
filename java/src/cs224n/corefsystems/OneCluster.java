package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class OneCluster implements CoreferenceSystem {

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		// TODO Auto-generated method stub
            if (trainingData!=null){
                
            }

	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
              List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
              List<Mention> mentionList = doc.getMentions();
              Entity entity = new Entity(mentionList);
                //(for each mention...)
              for(Mention m : mentionList){
                  mentions.add(m.markCoreferent(entity));
              }
                  return mentions;
	}

}
