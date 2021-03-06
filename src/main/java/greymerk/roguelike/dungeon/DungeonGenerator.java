package greymerk.roguelike.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import greymerk.roguelike.config.RogueConfig;
import greymerk.roguelike.dungeon.base.DungeonRoom;
import greymerk.roguelike.dungeon.base.IDungeonFactory;
import greymerk.roguelike.dungeon.base.IDungeonRoom;
import greymerk.roguelike.dungeon.settings.ISettings;
import greymerk.roguelike.dungeon.settings.LevelSettings;
import greymerk.roguelike.dungeon.towers.Tower;
import greymerk.roguelike.worldgen.Cardinal;
import greymerk.roguelike.worldgen.Coord;
import greymerk.roguelike.worldgen.IWorldEditor;
import greymerk.roguelike.worldgen.filter.Filter;

public class DungeonGenerator {
	public static final int VERTICAL_SPACING = 10;
	public static final int TOPLEVEL = 50;
	
	private Coord origin;
	private List<IDungeonLevel> levels;
	
	public DungeonGenerator(){
		this.levels = new ArrayList<IDungeonLevel>();
	}
	
	public void generate(IWorldEditor editor, ISettings settings, Coord pos){
		this.origin = new Coord(pos.getX(), TOPLEVEL, pos.getZ());
		Coord start = new Coord(this.origin);
		Random rand = Dungeon.getRandom(editor, start);
		int numLevels = settings.getNumLevels();
		
		// create level objects
		for (int i = 0; i < numLevels; ++i){
			LevelSettings levelSettings = settings.getLevelSettings(i);
			DungeonLevel level = new DungeonLevel(editor, rand, levelSettings, new Coord(start));
			this.levels.add(level);
		}
		
		/**** Create layouts and assign rooms ****/
		
		// generate level layouts
		for(IDungeonLevel level : this.levels){
			ILevelGenerator generator = LevelGenerator.getGenerator(editor, rand, level.getSettings().getGenerator(), level);
			
			try{
				level.generate(generator, start);
			} catch(Exception e){
				e.printStackTrace();
			}
			
			LevelLayout layout = generator.getLayout();
			rand = Dungeon.getRandom(editor, start);
			start = new Coord(layout.getEnd().getPosition());
			start.add(Cardinal.DOWN, VERTICAL_SPACING);
		}
		
		
		// assign dungeon rooms
		for(IDungeonLevel level : this.levels){
			LevelLayout layout = level.getLayout();
			IDungeonFactory rooms = level.getSettings().getRooms();
			
			int count = 0;
			while(layout.hasEmptyRooms()){
				IDungeonRoom toGenerate = count < level.getSettings().getNumRooms()
						? rooms.get(rand)
						: DungeonRoom.getInstance(DungeonRoom.CORNER);
				DungeonNode node = layout.getBestFit(toGenerate);
				node.setDungeon(toGenerate);
				++count;
			}
		}
		
		/**** Do actual worldgen tasks ****/
		
		// encase
		if(RogueConfig.getBoolean(RogueConfig.ENCASE)){
			for(IDungeonLevel level : this.levels){
				level.filter(editor, rand, Filter.get(Filter.ENCASE));
			}	
		}
		
		// generate tunnels
		for(IDungeonLevel level : this.levels){
			for(DungeonTunnel t : level.getLayout().getTunnels()){
				t.construct(editor, rand, level.getSettings());
			}
		}
		
		// generate rooms
		for(IDungeonLevel level : this.levels){
			LevelLayout layout = level.getLayout();
			List<DungeonNode> nodes = layout.getNodes();
			DungeonNode startRoom = layout.getStart();
			DungeonNode endRoom = layout.getEnd();
			for (DungeonNode node : nodes){
				if(node == startRoom || node == endRoom) continue;
				IDungeonRoom toGenerate = node.getRoom();
				toGenerate.generate(editor, rand, level.getSettings(), node.getEntrances(), node.getPosition());	
			}
		}
		
		// generate segments
		for(IDungeonLevel level : this.levels){
			for(DungeonTunnel tunnel : level.getLayout().getTunnels()){
				tunnel.genSegments(editor, rand, level);
			}
		}
		
		/**** Create level links & tower ****/
		
		// generate level links
		IDungeonLevel previous = null;
		for(IDungeonLevel level : this.levels){
			DungeonNode upper = previous == null ? null : previous.getLayout().getEnd();
			DungeonNode lower = level.getLayout().getStart();
			LevelGenerator.generateLevelLink(editor, rand, level.getSettings(), lower, upper);
			previous = level;
		}
		
		tower(editor, settings, origin);

	}
	
	private void tower(IWorldEditor editor, ISettings settings, Coord pos){
		Tower tower = settings.getTower().getTower();
		Random rand = Dungeon.getRandom(editor, new Coord(pos));
		Tower.get(tower).generate(editor, rand, settings.getTower().getTheme(), new Coord(pos));
	}
	
	public List<IDungeonLevel> getLevels(){
		return this.levels;
	}
	
	public boolean isGenerated(){
		return this.origin != null;
	}
	
	public Coord getPosition(){
		if(!this.isGenerated()){
			return null;
		}
		
		return new Coord(this.origin);
	}
	
	public void applyFilters(IWorldEditor editor, Random rand){
		for(IDungeonLevel level : this.levels){
			level.applyFilters(editor, rand);
		}
	}
}
