/*******************************************************************************
 * Copyright (c) 2015 btows.com.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package com.cleanwiz.applock.service;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import com.cleanwiz.applock.MyConstants;
import com.cleanwiz.applock.data.HideVideo;
import com.cleanwiz.applock.data.HideVideoDao.DaoMaster;
import com.cleanwiz.applock.data.HideVideoDao.DaoMaster.DevOpenHelper;
import com.cleanwiz.applock.data.HideVideoDao.DaoSession;
import com.cleanwiz.applock.data.HideVideoDao.HideVideoDao;
import com.cleanwiz.applock.data.HideVideoDao.HideVideoDao.Properties;
import com.cleanwiz.applock.files.utils.FileHideUtils;
import com.cleanwiz.applock.files.utils.SdCardUtil;
import com.cleanwiz.applock.model.AbstructProvider;
import com.cleanwiz.applock.model.VideoModel;
import com.cleanwiz.applock.utils.FileUtil;
import com.cleanwiz.applock.utils.LogUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VideoService implements AbstructProvider {

    private Context context;
    private String IMAGE_HIDE_URL = "";
    private HideVideoDao hideVideoDao = null;
    private DaoSession daoSession = null;

    public VideoService(Context context) {
        super();
        this.context = context;
        instanceHideVideoDataBase();
        LogUtil.e("colin", IMAGE_HIDE_URL);
    }

    public void instanceHideVideoDataBase() {
        if (hideVideoDao == null) {
            DevOpenHelper helper = new DaoMaster.DevOpenHelper(context,
                    MyConstants.getDatabasePath(context, "hidevideo"), null);
            SQLiteDatabase db = helper.getWritableDatabase();
            DaoMaster daoMaster = new DaoMaster(db);
            daoSession = daoMaster.newSession();
            hideVideoDao = daoSession.getHideVideoDao();
        }
    }

    // ???????????????????????????????????????????????????
    @Override
    public List<?> getList() {
        List<VideoModel> list = null;

        boolean needCheck = SdCardUtil.needCheckExtSDCard();
        String mPath = SdCardUtil.getExtSDCardPath();
        if (mPath == null)
            needCheck = false;

        if (context != null) {
            Cursor cursor = context.getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null,
                    null, null);
            if (cursor != null) {
                list = new ArrayList<VideoModel>();
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(cursor
                            .getColumnIndexOrThrow(MediaStore.Video.Media._ID));
                    String title = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.TITLE));
                    String album = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.ALBUM));
                    String artist = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST));
                    String displayName = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME));
                    String mimeType = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE));
                    String path = cursor
                            .getString(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                    long duration = cursor
                            .getInt(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));
                    long size = cursor
                            .getLong(cursor
                                    .getColumnIndexOrThrow(MediaStore.Video.Media.SIZE));

                    if (FileUtil.isHideFile(displayName))
                        continue;

                    // ????????????????????????
                    if (needCheck && path.contains(mPath)) {
                        continue;
                    }

                    VideoModel video = new VideoModel(id, title, album, artist,
                            displayName, mimeType, path, size, duration);
                    list.add(video);
                }
                cursor.close();
            }
        }
        return list;
    }

    // ??????????????????
    public boolean hideVideo(VideoModel videoModel, int beyondGroupId) {
        if (videoModel != null) {
            File fromFile = new File(videoModel.getPath());
            if (!fromFile.exists()) {
                return false;
            }
            String toPathString = MyConstants.getHidePath(videoModel.getPath());
            if (toPathString.isEmpty()) {
                return false;
            }
            File toFile = new File(toPathString + videoModel.getDisplayName() + MyConstants.getSuffix());
            // ??????
            if (fromFile.renameTo(toFile)) {
                // ???????????????
                if (hideVideoDao != null) {
                    long id = hideVideoDao.insertOrReplace(new HideVideo(null,
                            beyondGroupId, videoModel.getTitle(), videoModel
                            .getAlbum(), videoModel.getArtist(),
                            videoModel.getPath(), videoModel.getDisplayName(),
                            videoModel.getMimeType(), videoModel.getDuration(),
                            toFile.getPath(), videoModel.getSize(), new Date()
                            .getTime()));
                    if (id >= 0) {
                        delSysMedia(videoModel);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ???????????????????????????
    public boolean unHideVideo(HideVideo hideVideo) {
        if (hideVideo != null) {
            File fromFile = new File(hideVideo.getNewPathUrl());
            File toFile = new File(hideVideo.getOldPathUrl());
            // ???????????????
            if (hideVideoDao != null) {
                hideVideoDao.delete(hideVideo);
                insSysMedia(hideVideo);
            }

            // ??????
            if (fromFile.renameTo(toFile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ??????????????????????????????????????????????????????
     */
    private void delSysMedia(VideoModel mi) {
        ContentResolver cr = context.getContentResolver();
        cr.delete(Video.Media.EXTERNAL_CONTENT_URI, Video.Media._ID + "=?",
                new String[]{String.valueOf(mi.getId())});
        cr.delete(Video.Thumbnails.EXTERNAL_CONTENT_URI,
                Video.Thumbnails.VIDEO_ID + "=?",
                new String[]{String.valueOf(mi.getId())});
    }

    /**
     * ????????????????????????
     */
    private void insSysMedia(HideVideo mi) {
        ContentResolver cr = context.getContentResolver();
        File oriFile = new File(mi.getOldPathUrl());
        ContentValues values = new ContentValues();
        values.put(
                Video.Media.TITLE,
                mi.getDisplayName().substring(0,
                        mi.getDisplayName().lastIndexOf(".")));
        values.put(Video.Media.DISPLAY_NAME, mi.getDisplayName());
        values.put(Video.Media.DATA, mi.getOldPathUrl());
        values.put(Video.Media.DATE_MODIFIED, oriFile.lastModified());
        values.put(Video.Media.SIZE, oriFile.length());
        values.put(Video.Media.MIME_TYPE, mi.getMimeType());
        Uri contentUri = cr.insert(Video.Media.EXTERNAL_CONTENT_URI, values);
        if (contentUri != null) {
            mi.setId(ContentUris.parseId(contentUri));
        }
    }

    // ????????????????????????????????????
    public List<HideVideo> getHideVideos(int beyondGroupId) {
        List<HideVideo> hideVideos = new ArrayList<HideVideo>();
        if (hideVideoDao != null) {
            hideVideos = hideVideoDao.queryBuilder()
                    .where(Properties.BeyondGroupId.eq(beyondGroupId)).list();

            final List<HideVideo> list = FileHideUtils.checkHideVideo(hideVideos);
            if (list.size() > 0) {
                new Thread() {
                    @Override
                    public void run() {
                        for (HideVideo hideFile : list) {
                            deleteAudioByPath(hideFile);
                        }
                    }
                }.start();
            }
        }

        return hideVideos;
    }

    /**
     * ???????????????????????? ?????????ThumbnailUtils???????????????????????????????????????????????????ThumbnailUtils????????????????????????????????????
     * ?????????????????????????????????????????????MICRO_KIND?????????????????????MICRO_KIND??????kind?????????????????????????????????
     *
     * @param videoPath ???????????????
     * @param width     ????????????????????????????????????
     * @param height    ???????????????????????????????????????
     * @param kind      ??????MediaStore.Images.Thumbnails???????????????MINI_KIND???MICRO_KIND???
     *                  ?????????MINI_KIND: 512 x 384???MICRO_KIND: 96 x 96
     * @return ??????????????????????????????
     */
    public static Bitmap getVideoThumbnail(String videoPath, int width,
                                           int height, int kind) {
        Bitmap bitmap = null;
        // ????????????????????????
        bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, kind);
        if (bitmap == null) {
            return null;
        }
        LogUtil.e("colin", "w" + bitmap.getWidth());
        LogUtil.e("colin", "h" + bitmap.getHeight());
        bitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height,
                ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        return bitmap;
    }

    public int getHideVideoCount() {
        if (hideVideoDao != null) {
            List<HideVideo> hideVideoList = hideVideoDao.loadAll();
            return hideVideoList.size();
        }
        return 0;
    }

    // ????????????????????????
    public boolean deleteAudioByPath(HideVideo hideVideo) {
        if (hideVideo.getNewPathUrl() == null || hideVideo.getNewPathUrl().isEmpty()) {
            return false;
        }
        File videoFile = new File(hideVideo.getNewPathUrl());
        if (videoFile != null) {
            if (hideVideoDao != null) {
                hideVideoDao.delete(hideVideo);
            }
            if (videoFile.delete()) {
                delSysMedia(new VideoModel(hideVideo.getId().intValue(), hideVideo.getTitle(), hideVideo.getAlbum(), hideVideo.getArtist(), hideVideo.getDisplayName(), hideVideo.getMimeType(), hideVideo.getNewPathUrl(), hideVideo.getSize(), hideVideo.getDuration()));
                return true;
            }
        }
        return false;
    }

}
