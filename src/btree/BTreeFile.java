/*
 * @(#) bt.java   98/03/24
 * Copyright (c) 1998 UW.  All Rights Reserved.
 *         Author: Xiaohu Li (xioahu@cs.wisc.edu).
 *
 */

package btree;

import java.io.*;

import diskmgr.*;
import bufmgr.*;
import global.*;
import heap.*;
import btree.*;
/**
 * btfile.java This is the main definition of class BTreeFile, which derives
 * from abstract base class IndexFile. It provides an insert/delete interface.
 */
public class BTreeFile extends IndexFile implements GlobalConst {

	private final static int MAGIC0 = 1989;

	private final static String lineSep = System.getProperty("line.separator");

	private static FileOutputStream fos;
	private static DataOutputStream trace;

	/**
	 * It causes a structured trace to be written to a file. This output is used
	 * to drive a visualization tool that shows the inner workings of the b-tree
	 * during its operations.
	 *
	 * @param filename
	 *            input parameter. The trace file name
	 * @exception IOException
	 *                error from the lower layer
	 */
	public static void traceFilename(String filename) throws IOException {

		fos = new FileOutputStream(filename);
		trace = new DataOutputStream(fos);
	}

	/**
	 * Stop tracing. And close trace file.
	 *
	 * @exception IOException
	 *                error from the lower layer
	 */
	public static void destroyTrace() throws IOException {
		if (trace != null)
			trace.close();
		if (fos != null)
			fos.close();
		fos = null;
		trace = null;
	}

	private BTreeHeaderPage headerPage;
	private PageId headerPageId;
	private String dbname;

	/**
	 * Access method to data member.
	 * 
	 * @return Return a BTreeHeaderPage object that is the header page of this
	 *         btree file.
	 */
	public BTreeHeaderPage getHeaderPage() {
		return headerPage;
	}

	private PageId get_file_entry(String filename) throws GetFileEntryException {
		try {
			return SystemDefs.JavabaseDB.get_file_entry(filename);
		} catch (Exception e) {
			e.printStackTrace();
			throw new GetFileEntryException(e, "");
		}
	}

	private Page pinPage(PageId pageno) throws PinPageException {
		try {
			Page page = new Page();
			SystemDefs.JavabaseBM.pinPage(pageno, page, false/* Rdisk */);
			return page;
		} catch (Exception e) {
			e.printStackTrace();
			throw new PinPageException(e, "");
		}
	}

	private void add_file_entry(String fileName, PageId pageno)
			throws AddFileEntryException {
		try {
			SystemDefs.JavabaseDB.add_file_entry(fileName, pageno);
		} catch (Exception e) {
			e.printStackTrace();
			throw new AddFileEntryException(e, "");
		}
	}

	private void unpinPage(PageId pageno) throws UnpinPageException {
		try {
			SystemDefs.JavabaseBM.unpinPage(pageno, false /* = not DIRTY */);
		} catch (Exception e) {
			e.printStackTrace();
			throw new UnpinPageException(e, "");
		}
	}

	private void freePage(PageId pageno) throws FreePageException {
		try {
			SystemDefs.JavabaseBM.freePage(pageno);
		} catch (Exception e) {
			e.printStackTrace();
			throw new FreePageException(e, "");
		}

	}

	private void delete_file_entry(String filename)
			throws DeleteFileEntryException {
		try {
			SystemDefs.JavabaseDB.delete_file_entry(filename);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DeleteFileEntryException(e, "");
		}
	}

	private void unpinPage(PageId pageno, boolean dirty)
			throws UnpinPageException {
		try {
			SystemDefs.JavabaseBM.unpinPage(pageno, dirty);
		} catch (Exception e) {
			e.printStackTrace();
			throw new UnpinPageException(e, "");
		}
	}

	/**
	 * BTreeFile class an index file with given filename should already exist;
	 * this opens it.
	 *
	 * @param filename
	 *            the B+ tree file name. Input parameter.
	 * @exception GetFileEntryException
	 *                can not ger the file from DB
	 * @exception PinPageException
	 *                failed when pin a page
	 * @exception ConstructPageException
	 *                BT page constructor failed
	 */
	public BTreeFile(String filename) throws GetFileEntryException,
			PinPageException, ConstructPageException {

		headerPageId = get_file_entry(filename);

		headerPage = new BTreeHeaderPage(headerPageId);
		dbname = new String(filename);
		/*
		 * 
		 * - headerPageId is the PageId of this BTreeFile's header page; -
		 * headerPage, headerPageId valid and pinned - dbname contains a copy of
		 * the name of the database
		 */
	}

	/**
	 * if index file exists, open it; else create it.
	 *
	 * @param filename
	 *            file name. Input parameter.
	 * @param keytype
	 *            the type of key. Input parameter.
	 * @param keysize
	 *            the maximum size of a key. Input parameter.
	 * @param delete_fashion
	 *            full delete or naive delete. Input parameter. It is either
	 *            DeleteFashion.NAIVE_DELETE or DeleteFashion.FULL_DELETE.
	 * @exception GetFileEntryException
	 *                can not get file
	 * @exception ConstructPageException
	 *                page constructor failed
	 * @exception IOException
	 *                error from lower layer
	 * @exception AddFileEntryException
	 *                can not add file into DB
	 */
	public BTreeFile(String filename, int keytype, int keysize,
			int delete_fashion) throws GetFileEntryException,
			ConstructPageException, IOException, AddFileEntryException {

		headerPageId = get_file_entry(filename);
		if (headerPageId == null) // file not exist
		{
			headerPage = new BTreeHeaderPage();
			headerPageId = headerPage.getPageId();
			add_file_entry(filename, headerPageId);
			headerPage.set_magic0(MAGIC0);
			headerPage.set_rootId(new PageId(INVALID_PAGE));
			headerPage.set_keyType((short) keytype);
			headerPage.set_maxKeySize(keysize);
			headerPage.set_deleteFashion(delete_fashion);
			headerPage.setType(NodeType.BTHEAD);
		} else {
			headerPage = new BTreeHeaderPage(headerPageId);
		}

		dbname = new String(filename);

	}

	/**
	 * Close the B+ tree file. Unpin header page.
	 *
	 * @exception PageUnpinnedException
	 *                error from the lower layer
	 * @exception InvalidFrameNumberException
	 *                error from the lower layer
	 * @exception HashEntryNotFoundException
	 *                error from the lower layer
	 * @exception ReplacerException
	 *                error from the lower layer
	 */
	public void close() throws PageUnpinnedException,
			InvalidFrameNumberException, HashEntryNotFoundException,
			ReplacerException {
		if (headerPage != null) {
			SystemDefs.JavabaseBM.unpinPage(headerPageId, true);
			headerPage = null;
		}
	}

	/**
	 * Destroy entire B+ tree file.
	 *
	 * @exception IOException
	 *                error from the lower layer
	 * @exception IteratorException
	 *                iterator error
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception FreePageException
	 *                error when free a page
	 * @exception DeleteFileEntryException
	 *                failed when delete a file from DM
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception PinPageException
	 *                failed when pin a page
	 */
	public void destroyFile() throws IOException, IteratorException,
			UnpinPageException, FreePageException, DeleteFileEntryException,
			ConstructPageException, PinPageException {
		if (headerPage != null) {
			PageId pgId = headerPage.get_rootId();
			if (pgId.pid != INVALID_PAGE)
				_destroyFile(pgId);
			unpinPage(headerPageId);
			freePage(headerPageId);
			delete_file_entry(dbname);
			headerPage = null;
		}
	}

	private void _destroyFile(PageId pageno) throws IOException,
			IteratorException, PinPageException, ConstructPageException,
			UnpinPageException, FreePageException {

		BTSortedPage sortedPage;
		Page page = pinPage(pageno);
		sortedPage = new BTSortedPage(page, headerPage.get_keyType());

		if (sortedPage.getType() == NodeType.INDEX) {
			BTIndexPage indexPage = new BTIndexPage(page,
					headerPage.get_keyType());
			RID rid = new RID();
			PageId childId;
			KeyDataEntry entry;
			for (entry = indexPage.getFirst(rid); entry != null; entry = indexPage
					.getNext(rid)) {
				childId = ((IndexData) (entry.data)).getData();
				_destroyFile(childId);
			}
		} else { // BTLeafPage

			unpinPage(pageno);
			freePage(pageno);
		}

	}

	private void updateHeader(PageId newRoot) throws IOException,
			PinPageException, UnpinPageException {

		BTreeHeaderPage header;
		PageId old_data;

		header = new BTreeHeaderPage(pinPage(headerPageId));

		old_data = headerPage.get_rootId();
		header.set_rootId(newRoot);

		// clock in dirty bit to bm so our dtor needn't have to worry about it
		unpinPage(headerPageId, true /* = DIRTY */);

		// ASSERTIONS:
		// - headerPage, headerPageId valid, pinned and marked as dirty

	}

	/**
	 * insert record with the given key and rid
	 *
	 * @param key
	 *            the key of the record. Input parameter.
	 * @param rid
	 *            the rid of the record. Input parameter.
	 * @exception KeyTooLongException
	 *                key size exceeds the max keysize.
	 * @exception KeyNotMatchException
	 *                key is not integer key nor string key
	 * @exception IOException
	 *                error from the lower layer
	 * @exception LeafInsertRecException
	 *                insert error in leaf page
	 * @exception IndexInsertRecException
	 *                insert error in index page
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception NodeNotMatchException
	 *                node not match index page nor leaf page
	 * @exception ConvertException
	 *                error when convert between revord and byte array
	 * @exception DeleteRecException
	 *                error when delete in index page
	 * @exception IndexSearchException
	 *                error when search
	 * @exception IteratorException
	 *                iterator error
	 * @exception LeafDeleteException
	 *                error when delete in leaf page
	 * @exception InsertException
	 *                error when insert in index page
	 */
	public void insert(KeyClass key, RID rid) throws KeyTooLongException,
			KeyNotMatchException, LeafInsertRecException,
			IndexInsertRecException, ConstructPageException,
			UnpinPageException, PinPageException, NodeNotMatchException,
			ConvertException, DeleteRecException, IndexSearchException,
			IteratorException, LeafDeleteException, InsertException,
			IOException

	{
		KeyDataEntry  newRootEntry;
		//Check whether key is too long
		if (BT.getKeyLength(key) > headerPage.get_maxKeySize())
			throw new KeyTooLongException(null,"");
		//Check whether the type of key is right (integer)
		if ( key instanceof IntegerKey )
		{
			if ( headerPage.get_keyType() != AttrType.attrInteger )
			{
				throw new KeyNotMatchException(null,"");
			}
		}
		else
			throw new KeyNotMatchException(null,"");
	      /* Two Cases:
	      1. Header page root is invalid - 
	      	- the tree is empty and we have to create a leaf page;
	      2. Else - 
	      	Insert the pair (key, rid)*/
		if (headerPage.get_rootId().pid == INVALID_PAGE)
		{
			PageId newRootPageId;
			BTLeafPage newRootPage;
			newRootPage=new BTLeafPage( headerPage.get_keyType());
			newRootPageId=newRootPage.getCurPage();
			newRootPage.setNextPage(new PageId(INVALID_PAGE));
			newRootPage.setPrevPage(new PageId(INVALID_PAGE));
			// newRootPage, newRootPageId valid and pinned
			newRootPage.insertRecord(key, rid); 
			unpinPage(newRootPageId, true); /* dirty */
			updateHeader(newRootPageId);
			return;
		}
		newRootEntry= _insert(key, rid, headerPage.get_rootId());  
		/* Two Cases:
		1. newRootEntry is not null - 
		a leaf is split up to the root and the root split: the new pageNo is in newChildEntry.data.pageNo 
		2.newRootEntry is null -
		no new root was created 
		no page pinned  */
		if (newRootEntry != null)
		{
			BTIndexPage newRootPage;
			PageId      newRootPageId;
			Object      newEntryKey;//the information about the pair <key, PageId>
			newRootPage = new BTIndexPage(headerPage.get_keyType());
			newRootPageId=newRootPage.getCurPage();
			// - newRootPage, newRootPageId valid and pinned
			// - newEntryKey, newEntryPage contain the data for the new entry
			newRootPage.insertKey( newRootEntry.key, ((IndexData)newRootEntry.data).getData() );
		  // the old root split and is now the left child of the new root
		  newRootPage.setPrevPage(headerPage.get_rootId());
		  unpinPage(newRootPageId, true); /*dirty */
		  updateHeader(newRootPageId);
		}
	    return;
	}
	private KeyDataEntry _insert(KeyClass key, RID rid, PageId currentPageId)
			throws PinPageException, IOException, ConstructPageException,
			LeafDeleteException, ConstructPageException, DeleteRecException,
			IndexSearchException, UnpinPageException, LeafInsertRecException,
			ConvertException, IteratorException, IndexInsertRecException,
			KeyNotMatchException, NodeNotMatchException, InsertException

	{
		BTSortedPage currentPage;
	    Page page;
	    KeyDataEntry upEntry;
	    page=pinPage(currentPageId);
	    currentPage=new BTSortedPage(page, headerPage.get_keyType()); 
	    /* Two Cases:
	    1. Index Page - 
	    recurse and then split if necessary
	    2. Leaf Page - 
	    insert pair (key, rid)*/
	    if(currentPage.getType() == NodeType.INDEX)
	    {
	    	BTIndexPage  currentIndexPage=new BTIndexPage(page,headerPage.get_keyType());
	    	PageId currentIndexPageId = currentPageId;
	    	PageId nextPageId;
	    	nextPageId=currentIndexPage.getPageNoByKey(key);
	    	// now unpin the page, recurse and then pin it again
	    	unpinPage(currentIndexPageId);
	    	upEntry= _insert(key, rid, nextPageId);
	    	/* Two Cases:
	    	1. upEntry is null -
	    	 one level lower no split has occurred
	    	2. upEntry is not null -
	    	 one of the children has split*/
	    	if ( upEntry == null)
	    		return null;
	    	currentIndexPage= new  BTIndexPage(pinPage(currentPageId),headerPage.get_keyType());
	    	/* - upEntry is not null
	    	- currentIndexPage, currentIndexPageId valid and pinned	
	    	the information about the pair <key, PageId> is in upEntry*/
	    	if (currentIndexPage.available_space() >= BT.getKeyDataLength( upEntry.key, NodeType.INDEX))
	    	{
	    		// no split has occurred
	    		currentIndexPage.insertKey( upEntry.key,((IndexData)upEntry.data).getData() );
	    		unpinPage(currentIndexPageId, true );/* dirty */
	    		return null;
	    	}
	    	/* - On current index page enough space not available. Allocate new index page
	    	- currentIndexPage, currentIndexPageId valid and pinned*/
	    	BTIndexPage newIndexPage;
	    	PageId newIndexPageId;
	    	// Allocate a new INDEX page
	    	newIndexPage= new BTIndexPage(headerPage.get_keyType());
	    	newIndexPageId=newIndexPage.getCurPage();
	    	// - newIndexPage, newIndexPageId valid and pinned
	    	// - currentIndexPage, currentIndexPageId valid and pinned
	    	KeyDataEntry      tmpEntry;
	    	RID deleteRid=new RID();
	    	for ( tmpEntry= currentIndexPage.getFirst( deleteRid);tmpEntry!=null;tmpEntry= currentIndexPage.getFirst( deleteRid))
	    	{
	    		newIndexPage.insertKey( tmpEntry.key,((IndexData)tmpEntry.data).getData());
	    		currentIndexPage.deleteSortedRecord(deleteRid);
	    	}
	    	/* - currentIndexPage empty
	    	newIndexPage holds all former records from currentIndexPage
	    	Equal split is done*/
	    	RID firstRid=new RID();
	    	KeyDataEntry undoEntry=null;
	    	for (tmpEntry = newIndexPage.getFirst(firstRid);(currentIndexPage.available_space() >newIndexPage.available_space());tmpEntry=newIndexPage.getFirst(firstRid))
	    	{
	    		// insert the <key,pageId> pair on the new index page
	    		undoEntry=tmpEntry;
	    		currentIndexPage.insertKey( tmpEntry.key, ((IndexData)tmpEntry.data).getData());
	    		newIndexPage.deleteSortedRecord(firstRid);
	    	}
	    	//undo the final record
	    	if ( currentIndexPage.available_space() < newIndexPage.available_space())
	    	{
	    		newIndexPage.insertKey( undoEntry.key,((IndexData)undoEntry.data).getData());
	    		currentIndexPage.deleteSortedRecord(new RID(currentIndexPage.getCurPage(),(int)currentIndexPage.getSlotCnt()-1) );
	    	}
	    	// check whether <newKey, newIndexPageId> can be inserted
	    	tmpEntry= newIndexPage.getFirst(firstRid);
	    	if (BT.keyCompare( upEntry.key, tmpEntry.key) >=0 )
	    	{
	    		newIndexPage.insertKey( upEntry.key,((IndexData)upEntry.data).getData());
	    	}
	    	else
	    	{
	    		currentIndexPage.insertKey( upEntry.key,((IndexData)upEntry.data).getData());
	    		int i= (int)currentIndexPage.getSlotCnt()-1;
	    		tmpEntry = BT.getEntryFromBytes(currentIndexPage.getpage(),currentIndexPage.getSlotOffset(i),currentIndexPage.getSlotLength(i),headerPage.get_keyType(),NodeType.INDEX);
	    		newIndexPage.insertKey( tmpEntry.key,((IndexData)tmpEntry.data).getData());
	    		currentIndexPage.deleteSortedRecord(new RID(currentIndexPage.getCurPage(), i) );
	    	}
	    	unpinPage(currentIndexPageId, true );/* dirty */
	    	upEntry= newIndexPage.getFirst(deleteRid);
	    	newIndexPage.setPrevPage( ((IndexData)upEntry.data).getData());
	    	// delete first record on new index page since it is not required
	    	newIndexPage.deleteSortedRecord(deleteRid);  
	    	unpinPage(newIndexPageId, true );/* dirty */
	    	((IndexData)upEntry.data).setData( newIndexPageId);
	    	return upEntry; 
	    	/* - no pages pinned
	    	- upEntry holds the pointer to the KeyDataEntry which is
	    	to be inserted on the index page one level up*/
	    }	      
	    else if ( currentPage.getType()==NodeType.LEAF)
		{
	    	BTLeafPage currentLeafPage = new BTLeafPage(page,headerPage.get_keyType());
	    	PageId currentLeafPageId = currentPageId;
	    	// - currentLeafPage, currentLeafPageId valid and pinned
	    	if (currentLeafPage.available_space() >=BT.getKeyDataLength(key, NodeType.LEAF) )
		    {
	    		currentLeafPage.insertRecord(key, rid); 
	    		unpinPage(currentLeafPageId, true );/* dirty */
	    		return null;
	    	}
	    	// - If the code reaches here, there is not enough space on and split current leaf. Allocate a new leaf page and distribute the entries
	    	BTLeafPage  newLeafPage;
	    	PageId       newLeafPageId;
	    	//allocate a new leaf page and
	    	newLeafPage=new BTLeafPage(headerPage.get_keyType());
	    	newLeafPageId=newLeafPage.getCurPage();
	    	newLeafPage.setNextPage(currentLeafPage.getNextPage());
	    	newLeafPage.setPrevPage(currentLeafPageId);
	    	currentLeafPage.setNextPage(newLeafPageId);
	    	// change the prevPage pointer of next page
	    	PageId rightPageId;
	    	rightPageId = newLeafPage.getNextPage();
	    	if (rightPageId.pid != INVALID_PAGE)
		    {
	    		BTLeafPage rightPage;
	    		rightPage=new BTLeafPage(rightPageId, headerPage.get_keyType());
	    		rightPage.setPrevPage(newLeafPageId);
	    		unpinPage(rightPageId, true );/* dirty */
	    		// - newLeafPage, newLeafPageId valid and pinned
	    		// - currentLeafPage, currentLeafPageId valid and pinned
		    }
	    	KeyDataEntry     tmpEntry;
	    	RID       firstRid=new RID();
	    	for (tmpEntry = currentLeafPage.getFirst(firstRid);tmpEntry != null;tmpEntry = currentLeafPage.getFirst(firstRid))
		    {
	    		newLeafPage.insertRecord( tmpEntry.key,((LeafData)(tmpEntry.data)).getData());
	    		currentLeafPage.deleteSortedRecord(firstRid);
		    }
	    	// - currentLeafPage empty
	    	// - newLeafPage holds all previous records from currentLeafPage
	    	KeyDataEntry undoEntry=null; 
	    	for (tmpEntry = newLeafPage.getFirst(firstRid);newLeafPage.available_space() < currentLeafPage.available_space();tmpEntry=newLeafPage.getFirst(firstRid)   )
		    {
	    		undoEntry=tmpEntry;
	    		currentLeafPage.insertRecord( tmpEntry.key,((LeafData)tmpEntry.data).getData());
	    		newLeafPage.deleteSortedRecord(firstRid);		
		    }
	    	if (BT.keyCompare(key,undoEntry.key)<0)
	    	{
	    		if ( currentLeafPage.available_space() < newLeafPage.available_space())
	    		{
	    			newLeafPage.insertRecord( undoEntry.key,((LeafData)undoEntry.data).getData());
	    			currentLeafPage.deleteSortedRecord(new RID(currentLeafPage.getCurPage(),(int)currentLeafPage.getSlotCnt()-1) );
	    		}
	    	}	  
	    	// check whether <key, rid> can be inserted
	    	if (BT.keyCompare(key,undoEntry.key ) >= 0)
		    {
	    		// the new entry goes to the new Leaf page
	    		newLeafPage.insertRecord(key, rid);
	    	}
	    	else
	    	{
	    		currentLeafPage.insertRecord(key,rid);
	    	}
	    	unpinPage(currentLeafPageId, true /* dirty */);
	    	tmpEntry=newLeafPage.getFirst(firstRid);
	    	upEntry=new KeyDataEntry(tmpEntry.key, newLeafPageId );
	    	unpinPage(newLeafPageId, true );/* dirty */
	    	// - no pages pinned
	    	// - upEntry holds the valid KeyDataEntry which is to be inserted on the index page one level up
	    	return upEntry;
	    }
	    else
	    {
	    	throw new InsertException(null,"");
	    }
	}
	/**
	 * delete leaf entry given its <key, rid> pair. `rid' is IN the data entry;
	 * it is not the id of the data entry)
	 *
	 * @param key
	 *            the key in pair <key, rid>. Input Parameter.
	 * @param rid
	 *            the rid in pair <key, rid>. Input Parameter.
	 * @return true if deleted. false if no such record.
	 * @exception DeleteFashionException
	 *                neither full delete nor naive delete
	 * @exception LeafRedistributeException
	 *                redistribution error in leaf pages
	 * @exception RedistributeException
	 *                redistribution error in index pages
	 * @exception InsertRecException
	 *                error when insert in index page
	 * @exception KeyNotMatchException
	 *                key is neither integer key nor string key
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception IndexInsertRecException
	 *                error when insert in index page
	 * @exception FreePageException
	 *                error in BT page constructor
	 * @exception RecordNotFoundException
	 *                error delete a record in a BT page
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception IndexFullDeleteException
	 *                fill delete error
	 * @exception LeafDeleteException
	 *                delete error in leaf page
	 * @exception IteratorException
	 *                iterator error
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception DeleteRecException
	 *                error when delete in index page
	 * @exception IndexSearchException
	 *                error in search in index pages
	 * @exception IOException
	 *                error from the lower layer
	 *
	 */
	public boolean Delete(KeyClass key, RID rid) throws DeleteFashionException,
			LeafRedistributeException, RedistributeException,
			InsertRecException, KeyNotMatchException, UnpinPageException,
			IndexInsertRecException, FreePageException,
			RecordNotFoundException, PinPageException,
			IndexFullDeleteException, LeafDeleteException, IteratorException,
			ConstructPageException, DeleteRecException, IndexSearchException,
			IOException {
		if (headerPage.get_deleteFashion() == DeleteFashion.NAIVE_DELETE)
			return NaiveDelete(key, rid);
		else
			throw new DeleteFashionException(null, "");
	}
	/*
	 * findRunStart. Status BTreeFile::findRunStart (const void lo_key, RID
	 * *pstartrid)
	 * 
	 * find left-most occurrence of `lo_key', going all the way left if lo_key
	 * is null.
	 * 
	 * Starting record returned in *pstartrid, on page *pppage, which is pinned.
	 * 
	 * Since we allow duplicates, this must "go left" as described in the text
	 * (for the search algorithm).
	 * 
	 * @param lo_key find left-most occurrence of `lo_key', going all the way
	 * left if lo_key is null.
	 * 
	 * @param startrid it will reurn the first rid =< lo_key
	 * 
	 * @return return a BTLeafPage instance which is pinned. null if no key was
	 * found.
	 */
	BTLeafPage findRunStart(KeyClass lo_key, RID startrid) throws IOException,
			IteratorException, KeyNotMatchException, ConstructPageException,
			PinPageException, UnpinPageException {
		BTLeafPage pageLeaf;
		BTIndexPage pageIndex;
		Page page;
		BTSortedPage sortPage;
		PageId pageno;
		PageId curpageno = null; // iterator
		PageId prevpageno;
		PageId nextpageno;
		RID curRid;
		KeyDataEntry curEntry;

		pageno = headerPage.get_rootId();

		if (pageno.pid == INVALID_PAGE) { // no pages in the BTREE
			pageLeaf = null; // should be handled by
			// startrid =INVALID_PAGEID ; // the caller
			return pageLeaf;
		}

		page = pinPage(pageno);
		sortPage = new BTSortedPage(page, headerPage.get_keyType());

		if (trace != null) {
			trace.writeBytes("VISIT node " + pageno + lineSep);
			trace.flush();
		}

		// ASSERTION
		// - pageno and sortPage is the root of the btree
		// - pageno and sortPage valid and pinned

		while (sortPage.getType() == NodeType.INDEX) {
			pageIndex = new BTIndexPage(page, headerPage.get_keyType());
			prevpageno = pageIndex.getPrevPage();
			curEntry = pageIndex.getFirst(startrid);
			while (curEntry != null && lo_key != null
					&& BT.keyCompare(curEntry.key, lo_key) < 0) {

				prevpageno = ((IndexData) curEntry.data).getData();
				curEntry = pageIndex.getNext(startrid);
			}

			unpinPage(pageno);

			pageno = prevpageno;
			page = pinPage(pageno);
			sortPage = new BTSortedPage(page, headerPage.get_keyType());

			if (trace != null) {
				trace.writeBytes("VISIT node " + pageno + lineSep);
				trace.flush();
			}

		}

		pageLeaf = new BTLeafPage(page, headerPage.get_keyType());

		curEntry = pageLeaf.getFirst(startrid);
		while (curEntry == null) {
			// skip empty leaf pages off to left
			nextpageno = pageLeaf.getNextPage();
			unpinPage(pageno);
			if (nextpageno.pid == INVALID_PAGE) {
				// oops, no more records, so set this scan to indicate this.
				return null;
			}

			pageno = nextpageno;
			pageLeaf = new BTLeafPage(pinPage(pageno), headerPage.get_keyType());
			curEntry = pageLeaf.getFirst(startrid);
		}

		// ASSERTIONS:
		// - curkey, curRid: contain the first record on the
		// current leaf page (curkey its key, cur
		// - pageLeaf, pageno valid and pinned

		if (lo_key == null) {
			return pageLeaf;
			// note that pageno/pageLeaf is still pinned;
			// scan will unpin it when done
		}

		while (BT.keyCompare(curEntry.key, lo_key) < 0) {
			curEntry = pageLeaf.getNext(startrid);
			while (curEntry == null) { // have to go right
				nextpageno = pageLeaf.getNextPage();
				unpinPage(pageno);

				if (nextpageno.pid == INVALID_PAGE) {
					return null;
				}

				pageno = nextpageno;
				pageLeaf = new BTLeafPage(pinPage(pageno),
						headerPage.get_keyType());

				curEntry = pageLeaf.getFirst(startrid);
			}
		}

		return pageLeaf;
	}

	/*
	 * Status BTreeFile::NaiveDelete (const void *key, const RID rid)
	 * 
	 * Remove specified data entry (<key, rid>) from an index.
	 * 
	 * We don't do merging or redistribution, but do allow duplicates.
	 * 
	 * Page containing first occurrence of key `key' is found for us by
	 * findRunStart. We then iterate for (just a few) pages, if necesary, to
	 * find the one containing <key,rid>, which we then delete via
	 * BTLeafPage::delUserRid.
	 */

	private boolean NaiveDelete(KeyClass key, RID rid)
			throws LeafDeleteException, KeyNotMatchException, PinPageException,
			ConstructPageException, IOException, UnpinPageException,
			PinPageException, IndexSearchException, IteratorException
	{
		// remove the return statement and start your code.
		BTLeafPage leafPage;
	    RID curRid=new RID();
	    PageId nextpage;
	    KeyDataEntry entry;
	    leafPage=findRunStart(key, curRid);  // locate page,rid of key
	    if( leafPage == null) return false;  
	    entry=leafPage.getCurrent(curRid);  
	    while ( true )
	    {
	    	while ( entry == null)
	    	{
	    		nextpage = leafPage.getNextPage();
	    		unpinPage(leafPage.getCurPage());
	    		if (nextpage.pid == INVALID_PAGE)
	    		{
	    			return false;
	    		}
	    		leafPage=new BTLeafPage(pinPage(nextpage),headerPage.get_keyType() );
	    		entry=leafPage.getFirst(new RID());
	    	}
	    	if ( BT.keyCompare(key, entry.key) > 0 )
	    		break;
	    	if( leafPage.delEntry(new KeyDataEntry(key, rid)) ==true)
	    	{
	    		// <key, rid> found on this page and deleted it.
	    		// unpin dirty page
	    		unpinPage(leafPage.getCurPage(), true /* = DIRTY */);
	    		return true;
	    	}
	    	nextpage = leafPage.getNextPage();
	    	unpinPage(leafPage.getCurPage());
	    	leafPage=new BTLeafPage(pinPage(nextpage), headerPage.get_keyType());
	    	entry=leafPage.getFirst(curRid);
	    }  
	    /*
	    <key,rid> not found
	    */ 
	    unpinPage(leafPage.getCurPage());
	    return false;
	}
	/**
	 * create a scan with given keys Cases: (1) lo_key = null, hi_key = null
	 * scan the whole index (2) lo_key = null, hi_key!= null range scan from min
	 * to the hi_key (3) lo_key!= null, hi_key = null range scan from the lo_key
	 * to max (4) lo_key!= null, hi_key!= null, lo_key = hi_key exact match (
	 * might not unique) (5) lo_key!= null, hi_key!= null, lo_key < hi_key range
	 * scan from lo_key to hi_key
	 *
	 * @param lo_key
	 *            the key where we begin scanning. Input parameter.
	 * @param hi_key
	 *            the key where we stop scanning. Input parameter.
	 * @exception IOException
	 *                error from the lower layer
	 * @exception KeyNotMatchException
	 *                key is not integer key nor string key
	 * @exception IteratorException
	 *                iterator error
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception UnpinPageException
	 *                error when unpin a page
	 */
	public BTFileScan new_scan(KeyClass lo_key, KeyClass hi_key)
			throws IOException, KeyNotMatchException, IteratorException,
			ConstructPageException, PinPageException, UnpinPageException

	{
		BTFileScan scan = new BTFileScan();
		if (headerPage.get_rootId().pid == INVALID_PAGE) {
			scan.leafPage = null;
			return scan;
		}

		scan.treeFilename = dbname;
		scan.endkey = hi_key;
		scan.didfirst = false;
		scan.deletedcurrent = false;
		scan.curRid = new RID();
		scan.keyType = headerPage.get_keyType();
		scan.maxKeysize = headerPage.get_maxKeySize();
		scan.bfile = this;

		// this sets up scan at the starting position, ready for iteration
		scan.leafPage = findRunStart(lo_key, scan.curRid);
		return scan;
	}

	void trace_children(PageId id) throws IOException, IteratorException,
			ConstructPageException, PinPageException, UnpinPageException {

		if (trace != null) {

			BTSortedPage sortedPage;
			RID metaRid = new RID();
			PageId childPageId;
			KeyClass key;
			KeyDataEntry entry;
			sortedPage = new BTSortedPage(pinPage(id), headerPage.get_keyType());

			// Now print all the child nodes of the page.
			if (sortedPage.getType() == NodeType.INDEX) {
				BTIndexPage indexPage = new BTIndexPage(sortedPage,
						headerPage.get_keyType());
				trace.writeBytes("INDEX CHILDREN " + id + " nodes" + lineSep);
				trace.writeBytes(" " + indexPage.getPrevPage());
				for (entry = indexPage.getFirst(metaRid); entry != null; entry = indexPage
						.getNext(metaRid)) {
					trace.writeBytes("   " + ((IndexData) entry.data).getData());
				}
			} else if (sortedPage.getType() == NodeType.LEAF) {
				BTLeafPage leafPage = new BTLeafPage(sortedPage,
						headerPage.get_keyType());
				trace.writeBytes("LEAF CHILDREN " + id + " nodes" + lineSep);
				for (entry = leafPage.getFirst(metaRid); entry != null; entry = leafPage
						.getNext(metaRid)) {
					trace.writeBytes("   " + entry.key + " " + entry.data);
				}
			}
			unpinPage(id);
			trace.writeBytes(lineSep);
			trace.flush();
		}

	}

}
