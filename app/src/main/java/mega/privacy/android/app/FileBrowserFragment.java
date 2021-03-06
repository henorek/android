package mega.privacy.android.app;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mega.privacy.android.app.utils.Util;
import nz.mega.sdk.MegaAccountDetails;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaApiJava;
import nz.mega.sdk.MegaError;
import nz.mega.sdk.MegaGlobalListenerInterface;
import nz.mega.sdk.MegaNode;
import nz.mega.sdk.MegaRequest;
import nz.mega.sdk.MegaRequestListenerInterface;
import nz.mega.sdk.MegaShare;
import nz.mega.sdk.MegaTransfer;
import nz.mega.sdk.MegaUser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.util.DisplayMetrics;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


public class FileBrowserFragment extends Fragment implements OnClickListener, OnItemClickListener, OnItemLongClickListener, MegaRequestListenerInterface{

	public static int GRID_WIDTH =400;
	
	Context context;
	ActionBar aB;
	ListView listView;
	ImageView emptyImageView;
	TextView emptyTextView;
	MegaBrowserListAdapter adapterList;
	MegaBrowserNewGridAdapter adapterGrid;
	FileBrowserFragment fileBrowserFragment = this;
	LinearLayout buttonsLayout=null;	
	Button leftNewFolder;
	Button rightUploadButton;
	TextView contentText;
	LinearLayout outSpaceLayout=null;
	TextView outSpaceText;
	Button outSpaceButton;
	int usedSpacePerc;
	
	LinearLayout getProLayout=null;
	TextView getProText;
	Button leftCancelButton;
	Button rightUpgradeButton;
	
	MegaApiAndroid megaApi;
		
	long parentHandle = -1;
	boolean isList = true;
	int orderGetChildren = MegaApiJava.ORDER_DEFAULT_ASC;
	
	float scaleH, scaleW;
	float density;
	DisplayMetrics outMetrics;
	Display display;
	
	DatabaseHandler dbH;
	MegaPreferences prefs;
	
	ArrayList<MegaNode> nodes;
	
	HashMap<Long, MegaTransfer> mTHash = null;
	
	private ActionMode actionMode;
	
	private class ActionBarCallBack implements ActionMode.Callback {
		
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			List<MegaNode> documents = getSelectedDocuments();
			
			switch(item.getItemId()){
				case R.id.cab_menu_download:{
					ArrayList<Long> handleList = new ArrayList<Long>();
					for (int i=0;i<documents.size();i++){
						handleList.add(documents.get(i).getHandle());
					}
					clearSelections();
					hideMultipleSelect();
					((ManagerActivity) context).onFileClick(handleList);
					break;
				}
				case R.id.cab_menu_rename:{
					clearSelections();
					hideMultipleSelect();
					if (documents.size()==1){
						((ManagerActivity) context).showRenameDialog(documents.get(0), documents.get(0).getName());
					}
					break;
				}
				case R.id.cab_menu_copy:{
					ArrayList<Long> handleList = new ArrayList<Long>();
					for (int i=0;i<documents.size();i++){
						handleList.add(documents.get(i).getHandle());
					}
					clearSelections();
					hideMultipleSelect();
					((ManagerActivity) context).showCopy(handleList);
					break;
				}	
				case R.id.cab_menu_move:{
					ArrayList<Long> handleList = new ArrayList<Long>();
					for (int i=0;i<documents.size();i++){
						handleList.add(documents.get(i).getHandle());
					}
					clearSelections();
					hideMultipleSelect();
					((ManagerActivity) context).showMove(handleList);
					break;
				}
				case R.id.cab_menu_share:{
					//Check that all the selected options are folders
					ArrayList<Long> handleList = new ArrayList<Long>();
					for (int i=0;i<documents.size();i++){
						if(documents.get(i).isFolder()){
							handleList.add(documents.get(i).getHandle());
						}
					}
					clearSelections();
					hideMultipleSelect();
					((ManagerActivity) context).shareFolder(handleList);					
					break;
				}
				case R.id.cab_menu_send_file:{
					clearSelections();
					hideMultipleSelect();
					if (documents.size()==1){
						((ManagerActivity) context).sentToInbox(documents.get(0));
					}										
					break;
				}
				case R.id.cab_menu_share_link:{
					clearSelections();
					hideMultipleSelect();
					if (documents.size()==1){
						((ManagerActivity) context).getPublicLinkAndShareIt(documents.get(0));
					}
					break;
				}
				case R.id.cab_menu_trash:{
					ArrayList<Long> handleList = new ArrayList<Long>();
					for (int i=0;i<documents.size();i++){
						handleList.add(documents.get(i).getHandle());
					}
					clearSelections();
					hideMultipleSelect();
					((ManagerActivity) context).moveToTrash(handleList);
					break;
				}
				case R.id.cab_menu_select_all:{
					selectAll();
					break;
				}
				case R.id.cab_menu_unselect_all:{
					clearSelections();
					break;
				}
			}
			return false;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.file_browser_action, menu);
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode arg0) {
			adapterList.setMultipleSelect(false);
			listView.setOnItemLongClickListener(fileBrowserFragment);
			clearSelections();
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			List<MegaNode> selected = getSelectedDocuments();
			
			boolean showDownload = false;
			boolean showRename = false;
			boolean showCopy = false;
			boolean showMove = false;
			boolean showLink = false;
			boolean showTrash = false;
			boolean showShare = true;
			boolean showSendFile = false;
			// Rename
			if((selected.size() == 1) && (megaApi.checkAccess(selected.get(0), MegaShare.ACCESS_FULL).getErrorCode() == MegaError.API_OK)) {
				showRename = true;
			}
			
			// Link
			if ((selected.size() == 1) && (megaApi.checkAccess(selected.get(0), MegaShare.ACCESS_OWNER).getErrorCode() == MegaError.API_OK)) {
				showLink = true;
			}
			
			if ((selected.size() == 1) && (selected.get(0).isFile())){
				showSendFile = true;
			}
			
			if (selected.size() != 0) {
				showDownload = true;
				showTrash = true;
				showMove = true;
				showCopy = true;
				
				for(int i=0; i<selected.size();i++)	{
					if(megaApi.checkMove(selected.get(i), megaApi.getRubbishNode()).getErrorCode() != MegaError.API_OK)	{
						showTrash = false;
						showMove = false;
						break;
					}
					if(showShare){
						if(selected.get(i).isFile()){
							showShare = false;
						}
					}
				}
				
				if(selected.size()==adapterList.getCount()){
					menu.findItem(R.id.cab_menu_select_all).setVisible(false);
					menu.findItem(R.id.cab_menu_unselect_all).setVisible(true);			
				}
				else{
					menu.findItem(R.id.cab_menu_select_all).setVisible(true);
					menu.findItem(R.id.cab_menu_unselect_all).setVisible(true);	
				}	
			}
			else{
				menu.findItem(R.id.cab_menu_select_all).setVisible(true);
				menu.findItem(R.id.cab_menu_unselect_all).setVisible(false);
			}
			
			menu.findItem(R.id.cab_menu_download).setVisible(showDownload);
			menu.findItem(R.id.cab_menu_rename).setVisible(showRename);
			menu.findItem(R.id.cab_menu_copy).setVisible(showCopy);
			menu.findItem(R.id.cab_menu_move).setVisible(showMove);
			menu.findItem(R.id.cab_menu_leave_multiple_share).setVisible(false);
			menu.findItem(R.id.cab_menu_share_link).setVisible(showLink);
			menu.findItem(R.id.cab_menu_trash).setVisible(showTrash);
			menu.findItem(R.id.cab_menu_leave_multiple_share).setVisible(false);
			menu.findItem(R.id.cab_menu_share).setVisible(showShare);
			menu.findItem(R.id.cab_menu_send_file).setVisible(showSendFile);
			
			return false;
		}
		
	}
			
	@Override
	public void onCreate (Bundle savedInstanceState){
		if (megaApi == null){
			megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
		}
		
		dbH = DatabaseHandler.getDbHandler(context);
		prefs = dbH.getPreferences();
		
		super.onCreate(savedInstanceState);
		log("onCreate");	
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
		log("onCreateView");		
		
		if (megaApi == null){
			megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
		}
		
		if (aB == null){
			aB = ((ActionBarActivity)context).getSupportActionBar();
		}
		
		if (megaApi.getRootNode() == null){
			return null;
		}
		
		display = ((Activity)context).getWindowManager().getDefaultDisplay();
		outMetrics = new DisplayMetrics ();
	    display.getMetrics(outMetrics);
	    density  = getResources().getDisplayMetrics().density;
		
	    scaleW = Util.getScaleW(outMetrics, density);
	    scaleH = Util.getScaleH(outMetrics, density);
		
		if (parentHandle == -1){
			parentHandle = megaApi.getRootNode().getHandle();
			((ManagerActivity)context).setParentHandleBrowser(parentHandle);

			nodes = megaApi.getChildren(megaApi.getRootNode(), orderGetChildren);
			if(((ManagerActivity)context).getmDrawerToggle() != null)
			{
				aB.setTitle(getString(R.string.section_cloud_drive));	
				aB.setHomeAsUpIndicator(R.drawable.ic_menu_white);
				((ManagerActivity)context).getmDrawerToggle().setDrawerIndicatorEnabled(true);
				((ManagerActivity)context).supportInvalidateOptionsMenu();
			}
		}
		else{
			MegaNode parentNode = megaApi.getNodeByHandle(parentHandle);
			((ManagerActivity)context).setParentHandleBrowser(parentHandle);

			nodes = megaApi.getChildren(parentNode, orderGetChildren);
			
			if(((ManagerActivity)context).getmDrawerToggle() != null)
			{
				if (parentNode.getHandle() == megaApi.getRootNode().getHandle()){
					aB.setTitle(getString(R.string.section_cloud_drive));
					aB.setHomeAsUpIndicator(R.drawable.ic_menu_white);
					((ManagerActivity)context).getmDrawerToggle().setDrawerIndicatorEnabled(true);
				}
				else{
					aB.setTitle(parentNode.getName());					
					aB.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white);
					((ManagerActivity)context).getmDrawerToggle().setDrawerIndicatorEnabled(false);
				}
				((ManagerActivity)context).supportInvalidateOptionsMenu();
			}
		}	
		
		if (Util.CREATE_THUMB_PREVIEW_SERVICE){
			if (context != null){
				Intent intent = new Intent(context, CreateThumbPreviewService.class);
				intent.putExtra(CreateThumbPreviewService.EXTRA_PARENT_HASH, parentHandle);
				context.startService(intent);
			}
		}
		
		megaApi.getAccountDetails(this);
				
		if (isList){
			View v = inflater.inflate(R.layout.fragment_filebrowserlist, container, false);
	        
	        listView = (ListView) v.findViewById(R.id.file_list_view_browser);
			listView.setOnItemClickListener(this);
			listView.setOnItemLongClickListener(this);
			listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
			listView.setItemsCanFocus(false);
			
			emptyImageView = (ImageView) v.findViewById(R.id.file_list_empty_image);
			emptyTextView = (TextView) v.findViewById(R.id.file_list_empty_text);
			contentText = (TextView) v.findViewById(R.id.content_text);			
			
//			outSpaceButton.setVisibility(View.INVISIBLE);
			buttonsLayout = (LinearLayout) v.findViewById(R.id.buttons_layout);
			leftNewFolder = (Button) v.findViewById(R.id.btnLeft_new);
			rightUploadButton = (Button) v.findViewById(R.id.btnRight_upload);			
			
			leftNewFolder.setOnClickListener(this);
			rightUploadButton.setOnClickListener(this);
			
			outSpaceLayout = (LinearLayout) v.findViewById(R.id.out_space);
			outSpaceText =  (TextView) v.findViewById(R.id.out_space_text);
			outSpaceButton = (Button) v.findViewById(R.id.out_space_btn);
			outSpaceButton.setOnClickListener(this);
			
			getProLayout=(LinearLayout) v.findViewById(R.id.get_pro_account);
			getProText= (TextView) v.findViewById(R.id.get_pro_account_text);
			leftCancelButton = (Button) v.findViewById(R.id.btnLeft_cancel);
			rightUpgradeButton = (Button) v.findViewById(R.id.btnRight_upgrade);
			leftCancelButton.setOnClickListener(this);
			rightUpgradeButton.setOnClickListener(this);
			
			usedSpacePerc=((ManagerActivity)context).getUsedPerc();
			
			getProLayout.setVisibility(View.GONE);
			
			if(usedSpacePerc>95){
				
				//Change below of ListView
				log("usedSpacePerc>95");
				buttonsLayout.setVisibility(View.GONE);				
//				RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
//				p.addRule(RelativeLayout.ABOVE, R.id.out_space);
//				listView.setLayoutParams(p);
				outSpaceLayout.setVisibility(View.VISIBLE);
				outSpaceLayout.bringToFront();
				
				Handler handler = new Handler();
				handler.postDelayed(new Runnable() {					
					
					@Override
					public void run() {
						log("BUTTON DISAPPEAR");
						log("altura: "+outSpaceLayout.getHeight());
						
						TranslateAnimation animTop = new TranslateAnimation(0, 0, 0, outSpaceLayout.getHeight());
						animTop.setDuration(2000);
						animTop.setFillAfter(true);
						outSpaceLayout.setAnimation(animTop);
					
						outSpaceLayout.setVisibility(View.GONE);
						outSpaceLayout.invalidate();
//						RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
//						p.addRule(RelativeLayout.ABOVE, R.id.buttons_layout);
//						listView.setLayoutParams(p);
					}
				}, 15 * 1000);				
			}	
			else{
				outSpaceLayout.setVisibility(View.GONE);	    	   
			}

			if (adapterList == null){
				adapterList = new MegaBrowserListAdapter(context, nodes, parentHandle, listView, aB, ManagerActivity.FILE_BROWSER_ADAPTER);
				if (mTHash != null){
					adapterList.setTransfers(mTHash);
				}
			}
			else{
				adapterList.setParentHandle(parentHandle);
				adapterList.setNodes(nodes);
			}
			
			if (parentHandle == megaApi.getRootNode().getHandle()){
				MegaNode infoNode = megaApi.getRootNode();
				contentText.setText(getInfoFolder(infoNode));
				aB.setTitle(getString(R.string.section_cloud_drive));
				aB.setHomeAsUpIndicator(R.drawable.ic_menu_white);
			}
			else{
				MegaNode infoNode = megaApi.getNodeByHandle(parentHandle);
				contentText.setText(getInfoFolder(infoNode));
				aB.setTitle(infoNode.getName());
				aB.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white);
			}						
			
			adapterList.setPositionClicked(-1);
			adapterList.setMultipleSelect(false);

			listView.setAdapter(adapterList);			
			
			setNodes(nodes);
			
			if (adapterList.getCount() == 0){				
				
				listView.setVisibility(View.GONE);
				emptyImageView.setVisibility(View.VISIBLE);
				emptyTextView.setVisibility(View.VISIBLE);
				//TODO set below of the ListView
				leftNewFolder.setVisibility(View.VISIBLE);
				rightUploadButton.setVisibility(View.VISIBLE);
			}
			else{
				listView.setVisibility(View.VISIBLE);
				emptyImageView.setVisibility(View.GONE);
				emptyTextView.setVisibility(View.GONE);
				leftNewFolder.setVisibility(View.GONE);
				rightUploadButton.setVisibility(View.GONE);
			}					
			
			return v;
		}
		else{
			log("Grid View");
			
			View v = inflater.inflate(R.layout.fragment_filebrowsergrid, container, false);
			
			Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
			DisplayMetrics outMetrics = new DisplayMetrics ();
		    display.getMetrics(outMetrics);
		    float density  = ((Activity)context).getResources().getDisplayMetrics().density;
			
		    float scaleW = Util.getScaleW(outMetrics, density);
		    float scaleH = Util.getScaleH(outMetrics, density);
		    
		    int totalWidth = outMetrics.widthPixels;
		    int totalHeight = outMetrics.heightPixels;
		    
		    int numberOfCells = totalWidth / GRID_WIDTH;
		    if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
		    	if (numberOfCells < 3){
					numberOfCells = 3;
				}	
		    }
		    else if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
		    	if (numberOfCells < 2){
					numberOfCells = 2;
				}	
		    }
		    
		    listView = (ListView) v.findViewById(R.id.file_grid_view_browser);
			listView.setOnItemClickListener(null);
			listView.setItemsCanFocus(false);
		    
			emptyImageView = (ImageView) v.findViewById(R.id.file_grid_empty_image);
			emptyTextView = (TextView) v.findViewById(R.id.file_grid_empty_text);
			contentText = (TextView) v.findViewById(R.id.content_grid_text);
			
			buttonsLayout = (LinearLayout) v.findViewById(R.id.buttons_grid_layout);
			leftNewFolder = (Button) v.findViewById(R.id.btnLeft_grid_new);
			rightUploadButton = (Button) v.findViewById(R.id.btnRight_grid_upload);			
			
			leftNewFolder.setOnClickListener(this);
			rightUploadButton.setOnClickListener(this);
			
			getProLayout=(LinearLayout) v.findViewById(R.id.get_pro_account_grid);
			getProText= (TextView) v.findViewById(R.id.get_pro_account_text_grid);
			leftCancelButton = (Button) v.findViewById(R.id.btnLeft_cancel_grid);
			rightUpgradeButton = (Button) v.findViewById(R.id.btnRight_upgrade_grid);
			leftCancelButton.setOnClickListener(this);
			rightUpgradeButton.setOnClickListener(this);
			
			outSpaceLayout = (LinearLayout) v.findViewById(R.id.out_space_grid);
			outSpaceText =  (TextView) v.findViewById(R.id.out_space_text_grid);
			outSpaceButton = (Button) v.findViewById(R.id.out_space_btn_grid);
//			outSpaceButton.setVisibility(View.INVISIBLE);
			outSpaceButton.setOnClickListener(this);
			
			usedSpacePerc=((ManagerActivity)context).getUsedPerc();
			
			getProLayout.setVisibility(View.GONE);
			
			if(usedSpacePerc>95){
				//Change below of ListView
				log("usedSpacePerc>95");
				buttonsLayout.setVisibility(View.GONE);				
//				RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
//				p.addRule(RelativeLayout.ABOVE, R.id.out_space);
//				listView.setLayoutParams(p);
				outSpaceLayout.setVisibility(View.VISIBLE);
				outSpaceLayout.bringToFront();
				
				Handler handler = new Handler();
				handler.postDelayed(new Runnable() {					
					
					@Override
					public void run() {
						log("BUTTON DISAPPEAR");
						log("altura: "+outSpaceLayout.getHeight());
						
						TranslateAnimation animTop = new TranslateAnimation(0, 0, 0, outSpaceLayout.getHeight());
						animTop.setDuration(2000);
						animTop.setFillAfter(true);
						outSpaceLayout.setAnimation(animTop);
					
						outSpaceLayout.setVisibility(View.GONE);
						outSpaceLayout.invalidate();
//						RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
//						p.addRule(RelativeLayout.ABOVE, R.id.buttons_layout);
//						listView.setLayoutParams(p);
					}
				}, 15 * 1000);
				
			}	
			else{
				outSpaceLayout.setVisibility(View.GONE);
			}
			
			
//		    Toast.makeText(context, totalWidth + "x" + totalHeight + "= " + numberOfCells, Toast.LENGTH_LONG).show();
			
		    if (adapterGrid == null){
				adapterGrid = new MegaBrowserNewGridAdapter(context, nodes, parentHandle, listView, aB, numberOfCells, ManagerActivity.FILE_BROWSER_ADAPTER, orderGetChildren, emptyImageView, emptyTextView, leftNewFolder, rightUploadButton, contentText);
				if (mTHash != null){
					adapterGrid.setTransfers(mTHash);
				}
			}
			else{
				adapterGrid.setParentHandle(parentHandle);
				adapterGrid.setNodes(nodes);
			}
		    
			if (parentHandle == megaApi.getRootNode().getHandle()){
				MegaNode infoNode = megaApi.getRootNode();
				contentText.setText(getInfoFolder(infoNode));
				aB.setTitle(getString(R.string.section_cloud_drive));
				aB.setHomeAsUpIndicator(R.drawable.ic_menu_white);
			}
			else{
				MegaNode infoNode = megaApi.getRootNode();
				contentText.setText(getInfoFolder(infoNode));
				aB.setTitle(megaApi.getNodeByHandle(parentHandle).getName());
				aB.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white);
			}
			
			adapterGrid.setPositionClicked(-1);
			
			listView.setAdapter(adapterGrid);
			
			setNodes(nodes);
			
			if (adapterGrid.getCount() == 0){				
				
				listView.setVisibility(View.GONE);
				emptyImageView.setVisibility(View.VISIBLE);
				emptyTextView.setVisibility(View.VISIBLE);
				leftNewFolder.setVisibility(View.VISIBLE);
				rightUploadButton.setVisibility(View.VISIBLE);
			}
			else{
				listView.setVisibility(View.VISIBLE);
				contentText.setVisibility(View.VISIBLE);
				emptyImageView.setVisibility(View.GONE);
				emptyTextView.setVisibility(View.GONE);
				leftNewFolder.setVisibility(View.GONE);
				rightUploadButton.setVisibility(View.GONE);
			}				
			
			return v;
		}		
	}
		
	@Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        context = activity;
        aB = ((ActionBarActivity)activity).getSupportActionBar();
    }
	
	public void showAlertStorage(){
		log("showAlertStorage "+usedSpacePerc);
		
		usedSpacePerc=((ManagerActivity)context).getUsedPerc();
		log("PERCENTA: "+usedSpacePerc);
				
	}
		
	@Override
	public void onClick(View v) {
		
//		onClick = true;

		switch(v.getId()){
			case R.id.btnLeft_new:
				((ManagerActivity)getActivity()).showNewFolderDialog(null);				
				break;
				
			case R.id.btnRight_upload:
				((ManagerActivity)getActivity()).uploadFile();
				break;
			case R.id.btnLeft_grid_new:
				((ManagerActivity)getActivity()).showNewFolderDialog(null);				
				break;
				
			case R.id.btnRight_grid_upload:
				((ManagerActivity)getActivity()).uploadFile();
				break;
				
			case R.id.btnRight_upgrade:	
			case R.id.btnRight_upgrade_grid:	
			case R.id.out_space_btn:
				((ManagerActivity)getActivity()).upgradeAccountButton();
				break;
				
			case R.id.btnLeft_cancel_grid:	
			case R.id.btnLeft_cancel:
				getProLayout.setVisibility(View.GONE);
				break;
		}
	}
	
	private String getInfoFolder(MegaNode n) {
		int numFolders = megaApi.getNumChildFolders(n);
		int numFiles = megaApi.getNumChildFiles(n);

		String info = "";
		if (numFolders > 0) {
			info = numFolders
					+ " "
					+ context.getResources().getQuantityString(
							R.plurals.general_num_folders, numFolders);
			if (numFiles > 0) {
				info = info
						+ ", "
						+ numFiles
						+ " "
						+ context.getResources().getQuantityString(
								R.plurals.general_num_files, numFiles);
			}
		} else {
			info = numFiles
					+ " "
					+ context.getResources().getQuantityString(
							R.plurals.general_num_files, numFiles);
		}

		return info;
	}
	
	@Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		
		if (isList){
			if (adapterList.isMultipleSelect()){
				SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
				if (checkedItems.get(position, false) == true){
					listView.setItemChecked(position, true);
				}
				else{
					listView.setItemChecked(position, false);
				}				
				updateActionModeTitle();
				adapterList.notifyDataSetChanged();
			}
			else{
				if (nodes.get(position).isFolder()){
					MegaNode n = nodes.get(position);
					
//					if ((n.getName().compareTo(CameraSyncService.CAMERA_UPLOADS) == 0) && (megaApi.getParentNode(n).getType() == MegaNode.TYPE_ROOT)){
//						((ManagerActivity)context).cameraUploadsClicked();
//						return;
//					}
					prefs = dbH.getPreferences();
					String cameraSyncHandle = prefs.getCamSyncHandle();
					
					if(cameraSyncHandle!=null){
						if(!(cameraSyncHandle.equals("")))
						{
							if ((n.getHandle()==Long.parseLong(cameraSyncHandle))){
								((ManagerActivity)context).cameraUploadsClicked();
								return;
							}
						}
					}
					
					aB.setTitle(n.getName());
					aB.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white);
					((ManagerActivity)context).getmDrawerToggle().setDrawerIndicatorEnabled(false);
					((ManagerActivity)context).supportInvalidateOptionsMenu();
					
					parentHandle = nodes.get(position).getHandle();
					MegaNode infoNode = megaApi.getNodeByHandle(parentHandle);
					contentText.setText(getInfoFolder(infoNode));
					((ManagerActivity)context).setParentHandleBrowser(parentHandle);
					adapterList.setParentHandle(parentHandle);
					nodes = megaApi.getChildren(nodes.get(position), orderGetChildren);
					adapterList.setNodes(nodes);
					listView.setSelection(0);
					
					if (Util.CREATE_THUMB_PREVIEW_SERVICE){
						if (context != null){
							Intent intent = new Intent(context, CreateThumbPreviewService.class);
							intent.putExtra(CreateThumbPreviewService.EXTRA_PARENT_HASH, parentHandle);
							context.startService(intent);
						}
					}
					
					//If folder has no files
					if (adapterList.getCount() == 0){
						listView.setVisibility(View.GONE);
						emptyImageView.setVisibility(View.VISIBLE);
						emptyTextView.setVisibility(View.VISIBLE);
						leftNewFolder.setVisibility(View.VISIBLE);
						rightUploadButton.setVisibility(View.VISIBLE);

						if (megaApi.getRootNode().getHandle()==n.getHandle()) {
							emptyImageView.setImageResource(R.drawable.ic_empty_cloud_drive);
							emptyTextView.setText(R.string.file_browser_empty_cloud_drive);
						} else {
							emptyImageView.setImageResource(R.drawable.ic_empty_folder);
							emptyTextView.setText(R.string.file_browser_empty_folder);
						}
					}
					else{
						listView.setVisibility(View.VISIBLE);
						emptyImageView.setVisibility(View.GONE);
						emptyTextView.setVisibility(View.GONE);
						leftNewFolder.setVisibility(View.GONE);
						rightUploadButton.setVisibility(View.GONE);
					}
				}
				else{
					//Is file
					if (MimeTypeList.typeForName(nodes.get(position).getName()).isImage()){
						Intent intent = new Intent(context, FullScreenImageViewer.class);
						intent.putExtra("position", position);
						intent.putExtra("adapterType", ManagerActivity.FILE_BROWSER_ADAPTER);
						intent.putExtra("isFolderLink", false);
						if (megaApi.getParentNode(nodes.get(position)).getType() == MegaNode.TYPE_ROOT){
							intent.putExtra("parentNodeHandle", -1L);
						}
						else{
							intent.putExtra("parentNodeHandle", megaApi.getParentNode(nodes.get(position)).getHandle());
						}
						intent.putExtra("orderGetChildren", orderGetChildren);
						startActivity(intent);
								
					}
					else if (MimeTypeList.typeForName(nodes.get(position).getName()).isVideo() || MimeTypeList.typeForName(nodes.get(position).getName()).isAudio() ){
						MegaNode file = nodes.get(position);
						Intent service = new Intent(context, MegaStreamingService.class);
				  		context.startService(service);
				  		String fileName = file.getName();
						try {
							fileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
						} 
						catch (UnsupportedEncodingException e) {
							e.printStackTrace();
						}
						
				  		String url = "http://127.0.0.1:4443/" + file.getBase64Handle() + "/" + fileName;
				  		String mimeType = MimeTypeList.typeForName(file.getName()).getType();
				  		System.out.println("FILENAME: " + fileName);
				  		
				  		Intent mediaIntent = new Intent(Intent.ACTION_VIEW);
				  		mediaIntent.setDataAndType(Uri.parse(url), mimeType);
				  		if (ManagerActivity.isIntentAvailable(context, mediaIntent)){
				  			startActivity(mediaIntent);
				  		}
				  		else{
				  			Toast.makeText(context, context.getResources().getString(R.string.intent_not_available), Toast.LENGTH_LONG).show();
				  			adapterList.setPositionClicked(-1);
							adapterList.notifyDataSetChanged();
							ArrayList<Long> handleList = new ArrayList<Long>();
							handleList.add(nodes.get(position).getHandle());
							((ManagerActivity) context).onFileClick(handleList);
				  		}						
					}
					else{
						adapterList.setPositionClicked(-1);
						adapterList.notifyDataSetChanged();
						ArrayList<Long> handleList = new ArrayList<Long>();
						handleList.add(nodes.get(position).getHandle());
						((ManagerActivity) context).onFileClick(handleList);
					}
				}
			}
		}
		else{
			MegaNode infoNode = megaApi.getNodeByHandle(parentHandle);
			contentText.setText(getInfoFolder(infoNode));
			
			if (adapterGrid.getCount() == 0){				

				listView.setVisibility(View.GONE);
				emptyImageView.setVisibility(View.VISIBLE);
				emptyTextView.setVisibility(View.VISIBLE);
				leftNewFolder.setVisibility(View.VISIBLE);
				rightUploadButton.setVisibility(View.VISIBLE);
			}
			else{
				listView.setVisibility(View.VISIBLE);
				emptyImageView.setVisibility(View.GONE);
				emptyTextView.setVisibility(View.GONE);
				leftNewFolder.setVisibility(View.GONE);
				rightUploadButton.setVisibility(View.GONE);
			}			
		}
	}
	
	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		if (adapterList.getPositionClicked() == -1){
			clearSelections();
			actionMode = ((ActionBarActivity)context).startSupportActionMode(new ActionBarCallBack());
			listView.setItemChecked(position, true);
			adapterList.setMultipleSelect(true);
			updateActionModeTitle();
			listView.setOnItemLongClickListener(null);
		}
		return true;
	}
	
	public boolean showSelectMenuItem(){
		if (isList){
			if (adapterList != null){
				return adapterList.isMultipleSelect();
			}
		}
		else{
			if (adapterGrid != null){
				return adapterGrid.isMultipleSelect();
			}
		}
		
		return false;
	}
	
	public void selectAll(){
		if (isList){
			actionMode = ((ActionBarActivity)context).startSupportActionMode(new ActionBarCallBack());
	
			adapterList.setMultipleSelect(true);
			for ( int i=0; i< adapterList.getCount(); i++ ) {
				listView.setItemChecked(i, true);
			}
			updateActionModeTitle();
			listView.setOnItemLongClickListener(null);
		}
		else{
			if (adapterGrid != null){
				adapterGrid.selectAll();
			}
		}
	}
	
	/*
	 * Clear all selected items
	 */
	private void clearSelections() {
		SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
		for (int i = 0; i < checkedItems.size(); i++) {
			if (checkedItems.valueAt(i) == true) {
				int checkedPosition = checkedItems.keyAt(i);
				listView.setItemChecked(checkedPosition, false);
			}
		}
		updateActionModeTitle();
	}
	
	private void updateActionModeTitle() {
		if (actionMode == null || getActivity() == null) {
			return;
		}
		List<MegaNode> documents = getSelectedDocuments();
		int files = 0;
		int folders = 0;
		for (MegaNode document : documents) {
			if (document.isFile()) {
				files++;
			} else if (document.isFolder()) {
				folders++;
			}
		}
		Resources res = getActivity().getResources();
		String format = "%d %s";
		String filesStr = String.format(format, files,
				res.getQuantityString(R.plurals.general_num_files, files));
		String foldersStr = String.format(format, folders,
				res.getQuantityString(R.plurals.general_num_folders, folders));
		String title;
		if (files == 0 && folders == 0) {
			title = "";
		} else if (files == 0) {
			title = foldersStr;
		} else if (folders == 0) {
			title = filesStr;
		} else {
			title = foldersStr + ", " + filesStr;
		}
		actionMode.setTitle(title);
		try {
			actionMode.invalidate();
		} catch (NullPointerException e) {
			e.printStackTrace();
			log("oninvalidate error");
		}
		// actionMode.
	}
	
	/*
	 * Get list of all selected documents
	 */
	private List<MegaNode> getSelectedDocuments() {
		ArrayList<MegaNode> documents = new ArrayList<MegaNode>();
		SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
		for (int i = 0; i < checkedItems.size(); i++) {
			if (checkedItems.valueAt(i) == true) {
				MegaNode document = adapterList.getDocumentAt(checkedItems.keyAt(i));
				if (document != null){
					documents.add(document);
				}
			}
		}
		return documents;
	}
	
	/*
	 * Disable selection
	 */
	void hideMultipleSelect() {
		adapterList.setMultipleSelect(false);
		if (actionMode != null) {
			actionMode.finish();
		}
	}
	
	public int onBackPressed(){

		if (isList){
			if (adapterList != null){
				parentHandle = adapterList.getParentHandle();
				((ManagerActivity)context).setParentHandleBrowser(parentHandle);
				
				if (adapterList.isMultipleSelect()){
					hideMultipleSelect();
					return 3;
				}
				
				if (adapterList.getPositionClicked() != -1){
					adapterList.setPositionClicked(-1);
					adapterList.notifyDataSetChanged();
					return 1;
				}
				else{
					MegaNode parentNode = megaApi.getParentNode(megaApi.getNodeByHandle(parentHandle));				
					if (parentNode != null){
						listView.setVisibility(View.VISIBLE);
						emptyImageView.setVisibility(View.GONE);
						emptyTextView.setVisibility(View.GONE);
						leftNewFolder.setVisibility(View.GONE);
						rightUploadButton.setVisibility(View.GONE);
						if (parentNode.getHandle() == megaApi.getRootNode().getHandle()){
							aB.setTitle(getString(R.string.section_cloud_drive));	
							aB.setHomeAsUpIndicator(R.drawable.ic_menu_white);
							((ManagerActivity)context).getmDrawerToggle().setDrawerIndicatorEnabled(true);
						}
						else{
							aB.setTitle(parentNode.getName());					
							aB.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white);
							((ManagerActivity)context).getmDrawerToggle().setDrawerIndicatorEnabled(false);
						}
						
						((ManagerActivity)context).supportInvalidateOptionsMenu();
						
						parentHandle = parentNode.getHandle();
						((ManagerActivity)context).setParentHandleBrowser(parentHandle);
						nodes = megaApi.getChildren(parentNode, orderGetChildren);
						adapterList.setNodes(nodes);
						listView.post(new Runnable() 
					    {
					        @Override
					        public void run() 
					        {
					        	listView.setSelection(0);
					            View v = listView.getChildAt(0);
					            if (v != null) 
					            {
					                v.requestFocus();
					            }
					        }
					    });
						adapterList.setParentHandle(parentHandle);
						contentText.setText(getInfoFolder(parentNode));
						return 2;
					}
					else{
						return 0;
					}
				}
			}
		}
		else{
			if (adapterGrid != null){
				parentHandle = adapterGrid.getParentHandle();
				((ManagerActivity)context).setParentHandleBrowser(parentHandle);
				
				if (adapterGrid.isMultipleSelect()){
					adapterGrid.hideMultipleSelect();
					return 3;
				}
				
				if (adapterGrid.getPositionClicked() != -1){
					adapterGrid.setPositionClicked(-1);
					adapterGrid.notifyDataSetChanged();
					return 1;
				}
				else{
					MegaNode parentNode = megaApi.getParentNode(megaApi.getNodeByHandle(parentHandle));
					if (parentNode != null){
						listView.setVisibility(View.VISIBLE);
						emptyImageView.setVisibility(View.GONE);
						emptyTextView.setVisibility(View.GONE);
						leftNewFolder.setVisibility(View.GONE);
						rightUploadButton.setVisibility(View.GONE);
						if (parentNode.getHandle() == megaApi.getRootNode().getHandle()){
							aB.setTitle(getString(R.string.section_cloud_drive));	
							aB.setHomeAsUpIndicator(R.drawable.ic_menu_white);
							((ManagerActivity)context).getmDrawerToggle().setDrawerIndicatorEnabled(true);
						}
						else{
							aB.setTitle(parentNode.getName());					
							aB.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white);
							((ManagerActivity)context).getmDrawerToggle().setDrawerIndicatorEnabled(false);
						}
						
						((ManagerActivity)context).supportInvalidateOptionsMenu();
						
						parentHandle = parentNode.getHandle();
						((ManagerActivity)context).setParentHandleBrowser(parentHandle);
						nodes = megaApi.getChildren(parentNode, orderGetChildren);
						adapterGrid.setNodes(nodes);
						listView.post(new Runnable() 
					    {
					        @Override
					        public void run() 
					        {
					        	listView.setSelection(0);
					            View v = listView.getChildAt(0);
					            if (v != null) 
					            {
					                v.requestFocus();
					            }
					        }
					    });
						adapterGrid.setParentHandle(parentHandle);
						contentText.setText(getInfoFolder(parentNode));
						return 2;
					}
					else{
						return 0;
					}
				}
			}
		}
		
		return 0;
	}
	
	public long getParentHandle(){
		if (isList){
			if (adapterList != null){
				return adapterList.getParentHandle();
			}
			else{
				return -1;
			}
		}
		else{
			if (adapterGrid != null){
				return adapterGrid.getParentHandle();
			}
			else{
				return -1;
			}
		}
	}
	
	public void setParentHandle(long parentHandle){
		this.parentHandle = parentHandle;
		if (isList){
			if (adapterList != null){
				adapterList.setParentHandle(parentHandle);
			}
		}
		else{
			if (adapterGrid != null){
				adapterGrid.setParentHandle(parentHandle);
			}
		}
	}
	
	public ListView getListView(){
		return listView;
	}
	
	public void setNodes(ArrayList<MegaNode> nodes){
		this.nodes = nodes;
		if (isList){
			if (adapterList != null){
				adapterList.setNodes(nodes);
				if (adapterList.getCount() == 0){
					listView.setVisibility(View.GONE);
					emptyImageView.setVisibility(View.VISIBLE);
					emptyTextView.setVisibility(View.VISIBLE);
					leftNewFolder.setVisibility(View.VISIBLE);
					rightUploadButton.setVisibility(View.VISIBLE);
					if (megaApi.getRootNode().getHandle()==parentHandle) {
						emptyImageView.setImageResource(R.drawable.ic_empty_cloud_drive);
						emptyTextView.setText(R.string.file_browser_empty_cloud_drive);
					} else {
						emptyImageView.setImageResource(R.drawable.ic_empty_folder);
						emptyTextView.setText(R.string.file_browser_empty_folder);
					}
				}
				else{
					listView.setVisibility(View.VISIBLE);
					emptyImageView.setVisibility(View.GONE);
					emptyTextView.setVisibility(View.GONE);
					leftNewFolder.setVisibility(View.GONE);
					rightUploadButton.setVisibility(View.GONE);
				}			
			}	
		}
		else{
			if (adapterGrid != null){
				adapterGrid.setNodes(nodes);
				if (adapterGrid.getCount() == 0){
					listView.setVisibility(View.GONE);
					emptyImageView.setVisibility(View.VISIBLE);
					emptyTextView.setVisibility(View.VISIBLE);
					leftNewFolder.setVisibility(View.VISIBLE);
					rightUploadButton.setVisibility(View.VISIBLE);
					if (megaApi.getRootNode().getHandle()==parentHandle) {
						emptyImageView.setImageResource(R.drawable.ic_empty_cloud_drive);
						emptyTextView.setText(R.string.file_browser_empty_cloud_drive);
					} else {
						emptyImageView.setImageResource(R.drawable.ic_empty_folder);
						emptyTextView.setText(R.string.file_browser_empty_folder);
					}
				}
				else{
					listView.setVisibility(View.VISIBLE);
					emptyImageView.setVisibility(View.GONE);
					emptyTextView.setVisibility(View.GONE);
					leftNewFolder.setVisibility(View.GONE);
					rightUploadButton.setVisibility(View.GONE);
				}			
			}
		}
	}
	
	public void setPositionClicked(int positionClicked){
		if (isList){
			if (adapterList != null){
				adapterList.setPositionClicked(positionClicked);
			}
		}
		else{
			if (adapterGrid != null){
				adapterGrid.setPositionClicked(positionClicked);
			}	
		}		
	}
	
	public void notifyDataSetChanged(){
		if (isList){
			if (adapterList != null){
				adapterList.notifyDataSetChanged();
			}
		}
		else{
			if (adapterGrid != null){
				adapterGrid.notifyDataSetChanged();
			}
		}
	}
	
	public void setIsList(boolean isList){
		this.isList = isList;
	}
	
	public boolean getIsList(){
		return isList;
	}
	
	public void setOrder(int orderGetChildren){
		this.orderGetChildren = orderGetChildren;
		if (isList){
			if (adapterList != null){
				adapterList.setOrder(orderGetChildren);
			}
		}
		else{
			if (adapterGrid != null){
				adapterGrid.setOrder(orderGetChildren);
			}
		}
	}
	
	public void setTransfers(HashMap<Long, MegaTransfer> _mTHash){
		this.mTHash = _mTHash;
		
		if (isList){
			if (adapterList != null){
				adapterList.setTransfers(mTHash);
			}
		}
		else{
			if (adapterGrid != null){
				adapterGrid.setTransfers(mTHash);
			}
		}	
	
	}
	
	public void setCurrentTransfer(MegaTransfer mT){
		if (isList){
			if (adapterList != null){
				adapterList.setCurrentTransfer(mT);
			}
		}
		else{
			if (adapterGrid != null){
				adapterGrid.setCurrentTransfer(mT);
			}
		}	
		
		
	}
	
	private static void log(String log) {
		Util.log("FileBrowserFragment", log);
	}
	
	public void setContentText(){
		
		if (parentHandle == megaApi.getRootNode().getHandle()){
			MegaNode infoNode = megaApi.getRootNode();
			if (infoNode !=  null){
				contentText.setText(getInfoFolder(infoNode));
				aB.setTitle(getString(R.string.section_cloud_drive));
				aB.setHomeAsUpIndicator(R.drawable.ic_menu_white);
			}
		}
		else{
			MegaNode infoNode = megaApi.getNodeByHandle(parentHandle);
			if (infoNode !=  null){
				contentText.setText(getInfoFolder(infoNode));
				aB.setTitle(infoNode.getName());
				aB.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white);
			}
		}
	}

	@Override
	public void onRequestStart(MegaApiJava api, MegaRequest request) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onRequestUpdate(MegaApiJava api, MegaRequest request) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onRequestFinish(MegaApiJava api, MegaRequest request,
			MegaError e) {
		if (request.getType() == MegaRequest.TYPE_ACCOUNT_DETAILS){
			log ("account_details request");
			if (e.getErrorCode() == MegaError.API_OK)
			{
				MegaAccountDetails accountInfo = request.getMegaAccountDetails();
				
				int accountType = accountInfo.getProLevel();
				switch(accountType){				
				
					case 0:{	
						log("account FREE");
						if(usedSpacePerc<96){
							log("usedSpacePerc<96");
							if(Util.showMessageRandom()){
					    		log("Random: TRUE");
					    		getProLayout.setVisibility(View.VISIBLE);
					    		getProLayout.bringToFront();
					    	}
					    	else{
					    		log("Random: FALSO");
					    		getProLayout.setVisibility(View.GONE);
					    	}			 
						}
						break;
					}			
					
				}
			}
		}		
	}

	@Override
	public void onRequestTemporaryError(MegaApiJava api, MegaRequest request,
			MegaError e) {
		// TODO Auto-generated method stub
		
	}
}
