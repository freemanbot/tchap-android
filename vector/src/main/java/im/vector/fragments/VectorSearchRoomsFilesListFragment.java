/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.AbstractMessagesAdapter;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.message.FileMessage;
import org.matrix.androidsdk.rest.model.message.ImageMessage;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.message.VideoMessage;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;

import fr.gouv.tchap.media.AntiVirusScanStatus;
import fr.gouv.tchap.model.MediaScan;
import im.vector.activity.VectorMediasViewerActivity;
import im.vector.adapters.VectorMessagesAdapter;
import im.vector.adapters.VectorSearchFilesListAdapter;
import im.vector.util.SlidableMediaInfo;

public class VectorSearchRoomsFilesListFragment extends VectorSearchMessagesListFragment {
    /**
     * static constructor
     *
     * @param matrixId    the session Id.
     * @param layoutResId the used layout.
     * @return the instance
     */
    public static VectorSearchRoomsFilesListFragment newInstance(String matrixId, String roomId, int layoutResId) {
        VectorSearchRoomsFilesListFragment frag = new VectorSearchRoomsFilesListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);

        if (null != roomId) {
            args.putString(ARG_ROOM_ID, roomId);
        }

        frag.setArguments(args);
        return frag;
    }

    @Override
    public AbstractMessagesAdapter createMessagesAdapter() {
        mIsMediaSearch = true;
        VectorSearchFilesListAdapter vectorSearchFilesListAdapter = new VectorSearchFilesListAdapter(mSession, getActivity(), (null == mRoomId), getMXMediasCache());
        // Add the current media scan manager if any
        if (null != mMediaScanManager) {
            vectorSearchFilesListAdapter.setMediaScanManager(mMediaScanManager);
        }
        return vectorSearchFilesListAdapter;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        mMessageListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // CAUTION: We have to check here the scan result for the clicked media, because the click listener is enabled on the whole list view.
                // and the untrusted media are not removed from the search result for the moment.
                MessageRow row = mAdapter.getItem(position);
                Event event = row.getEvent();

                VectorMessagesAdapter vectorMessagesAdapter = (VectorMessagesAdapter) mAdapter;

                if (vectorMessagesAdapter.isInSelectionMode()) {
                    // cancel the selection mode.
                    vectorMessagesAdapter.onEventTap(null);
                    return;
                }

                Message message = JsonUtils.toMessage(event.getContent());

                // Video and images are displayed inside a medias slider.
                if (Message.MSGTYPE_IMAGE.equals(message.msgtype) || (Message.MSGTYPE_VIDEO.equals(message.msgtype))) {
                    // Check whether the media is trusted
                    boolean isTrusted = false;
                    String url = null;
                    String thumbnailUrl = null;

                    if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
                        ImageMessage imageMessage = JsonUtils.toImageMessage(event.getContent());
                        url = imageMessage.getUrl();
                        thumbnailUrl = imageMessage.getThumbnailUrl();
                    } else {
                        VideoMessage videoMessage = JsonUtils.toVideoMessage(event.getContent());
                        url = videoMessage.getUrl();
                        thumbnailUrl = videoMessage.getThumbnailUrl();
                    }

                    if (null != url) {
                        AntiVirusScanStatus antiVirusScanStatus = AntiVirusScanStatus.UNKNOWN;

                        if (null != mMediaScanManager) {
                            MediaScan mediaScan = mMediaScanManager.scanMedia(url);
                            antiVirusScanStatus = mediaScan.getAntiVirusScanStatus();
                        }

                        switch (antiVirusScanStatus) {
                            case TRUSTED:
                                // Check the thumbnail url (if any)
                                if (null != thumbnailUrl) {
                                    MediaScan mediaScan = mMediaScanManager.scanMedia(thumbnailUrl);
                                    antiVirusScanStatus = mediaScan.getAntiVirusScanStatus();
                                    switch (antiVirusScanStatus) {
                                        case TRUSTED:
                                            isTrusted = true;
                                            break;
                                    }
                                } else {
                                    isTrusted = true;
                                }
                                break;
                        }
                    }

                    if (isTrusted) {
                        // Retrieve the trusted slidable medias
                        ArrayList<SlidableMediaInfo> mediaMessagesList = listSlidableMessages();
                        int listPosition = getMediaMessagePosition(mediaMessagesList, message);

                        if (listPosition >= 0) {
                            Intent viewImageIntent = new Intent(getActivity(), VectorMediasViewerActivity.class);

                            viewImageIntent.putExtra(VectorMediasViewerActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
                            viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_WIDTH, mAdapter.getMaxThumbnailWidth());
                            viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_THUMBNAIL_HEIGHT, mAdapter.getMaxThumbnailHeight());
                            viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_INFO_LIST, mediaMessagesList);
                            viewImageIntent.putExtra(VectorMediasViewerActivity.KEY_INFO_LIST_INDEX, listPosition);

                            getActivity().startActivity(viewImageIntent);
                        }
                    }
                } else if (Message.MSGTYPE_FILE.equals(message.msgtype)) {
                    FileMessage fileMessage = JsonUtils.toFileMessage(event.getContent());
                    String url = fileMessage.getUrl();

                    // Check whether the media is trusted
                    MediaScan mediaScan = mMediaScanManager.scanMedia(url);
                    AntiVirusScanStatus antiVirusScanStatus = mediaScan.getAntiVirusScanStatus();

                    if (null != url && antiVirusScanStatus == AntiVirusScanStatus.TRUSTED) {
                        onMediaAction(ACTION_VECTOR_OPEN, url, fileMessage.getMimeType(), fileMessage.body, fileMessage.file);
                    }
                }
            }
        });

        return view;
    }
}
