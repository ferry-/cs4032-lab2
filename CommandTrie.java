import java.util.Arrays;

public class CommandTrie {
	private static class TrieNode {
		public TrieNode[] data = null;
		public TrieNode() { }
		//inserts node if not present, only allocates when necessary
		//returns whether no action was done (no divergence from existing content)
		public boolean query(int idx) {
			if (data == null) {
				data = new TrieNode[256];
				data[idx] = new TrieNode();
				return false;
			}
			else {
				if (data[idx] == null) {
					data[idx] = new TrieNode();
					return false;
				}
				else {
					//no change
					return true;
				}
			}
		}

		public boolean isLeaf() {
			return data == null;
		}
	}

	//thread safe so long as enclosing CommandTrie instance does not change its trie
	public class TrieMatcher { //non-static so can't build without factory
		private TrieNode at;
		private byte[] matched;
		private int idx;
		public TrieMatcher(TrieNode start, int longest) {
			at = start;
			matched = new byte[longest];
			idx = 0;
		}

		//return true until cannot advance any more, two outcomes when that happens:
		//1. isDone() returns false - traversal not part of the trie
		//2. isDone() returns true - reached a valid leaf node, can call getMatched() for data
		public boolean advance(byte b) {
			int ub = (int)b + 127; //unsignify
			TrieNode next = at.data[ub];
			if (next != null) {
				matched[idx++] = b;
				at = next;
				if (next.data == null) return false; //end of the traversal
				else return true;
			}
			else {
				at = null; //should not call advance again on this
				return false;
			}
		}

		public boolean isDone() {
			return (at != null) && at.isLeaf();
		}

		public byte[] getMatched() {
			if (isDone())
				return Arrays.copyOf(matched, idx);
			else 
				return null;
		}
	}

	private TrieNode root = new TrieNode();
	private int longest = 0;
	public CommandTrie() { }

	public boolean addRule(byte[] command) {
		boolean ambiguous = true;
		TrieNode at = root;
		
		for (int i = 0; i < command.length; i++) {
			int idx = (int)command[i] + 127; //unsignify
			ambiguous = at.query(idx) && ambiguous;
			at = at.data[idx];
		}
		if (!ambiguous && command.length > longest) {
			longest = command.length;
		}

		return !ambiguous;
	}

	//only TrieMatcher facotry
	public TrieMatcher matcher() {
		return new TrieMatcher(root, longest);
	}
}
