package com.lenovo.arcloud.mq.dao.hbase;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.lenovo.arcloud.mq.dao.ImageDao;
import com.lenovo.arcloud.mq.hbase.HbaseAccessException;
import com.lenovo.arcloud.mq.hbase.HbaseOperations;
import com.lenovo.arcloud.mq.hbase.HbaseTables;
import com.lenovo.arcloud.mq.model.ImageObj;
import com.lenovo.arcloud.mq.util.ConstantUtil;
import com.lenovo.arcloud.mq.util.FileUtils;
import com.lenovo.arcloud.mq.util.RowKeyUtils;
import com.lenovo.arcloud.mq.util.StringUtils;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.*;
import java.net.URL;
import java.util.List;

/***
 * Description
 *
 * @author zhulc1@lenovo.com
 * @since 2017/3/29
 *
 */
@Service
public class HbaseImageDao implements ImageDao {

    @Resource
    private HbaseOperations hbaseTemplate;

    @Override
    public void insert(ImageObj image) {
        Put put = imageObjToPut(image);
        hbaseTemplate.put(HbaseTables.IMAGE,put);
    }

    @Override
    public void insert(List<ImageObj> imageObjList) {
        List<Put> puts = Lists.newArrayListWithCapacity(imageObjList.size());
        for (ImageObj imageObj : imageObjList) {
            puts.add(imageObjToPut(imageObj));
        }
        hbaseTemplate.put(HbaseTables.IMAGE,puts);
    }

    private Put imageObjToPut(ImageObj imageObj) {
        byte[] rowKey = getRowKey(imageObj);
        Put p = new Put(rowKey);
        if (!StringUtils.isEmpty(imageObj.getName())) {
            p.addColumn(HbaseTables.IMAGE_META_INFO, HbaseTables.IMAGE_META_INFO_NAME, Bytes.toBytes(imageObj.getName()));
        }
        if (!StringUtils.isEmpty(imageObj.getPath())) {
            p.addColumn(HbaseTables.IMAGE_META_INFO, HbaseTables.IMAGE_META_INFO_PATH, Bytes.toBytes(imageObj.getPath()));
        }
        if (!StringUtils.isEmpty(imageObj.getExt())) {
            p.addColumn(HbaseTables.IMAGE_META_INFO, HbaseTables.IMAGE_META_INFO_EXT, Bytes.toBytes(imageObj.getExt()));
        }
        if (!StringUtils.isEmpty(imageObj.getPath())) {
            try {
                InputStream inputStream;
                if (imageObj.getPath().indexOf("http:") >= 0) {
                    URL url = new URL(imageObj.getPath());
                    inputStream = url.openStream();
                } else {
                    inputStream = new FileInputStream(imageObj.getPath());
                }
                byte[] bytes = ByteStreams.toByteArray(inputStream);
                p.addColumn(HbaseTables.IMAGE_MOB, HbaseTables.IMAGE_MOB, bytes);
                imageObj.setCheckSum(FileUtils.getCRC16(bytes));
                inputStream.close();
            } catch (Exception e) {
                throw new HbaseAccessException("image target is >>>" + imageObj.getPath(), e);
            }

        }
        if (!StringUtils.isEmpty(imageObj.getCheckSum())) {
            p.addColumn(HbaseTables.IMAGE_META_INFO, HbaseTables.IMAGE_META_INFO_CKSUM, Bytes.toBytes(imageObj.getCheckSum()));
        }

       /* BufferedImage image;
        if (!StringUtils.isEmpty(imageObj.getPath())) {
            if (imageObj.getPath().indexOf("http:") >= 0) {
                try {
                    URL imageUrl = new URL(imageObj.getPath());
                    image = ImageIO.read(imageUrl);
                } catch (MalformedURLException e) {
                    throw new HbaseAccessException("url format is not right>>>" + imageObj.getPath(), e);
                } catch (IOException ie) {
                    throw new HbaseAccessException("url target is not exist>>>" + imageObj.getPath(), ie);
                }
            } else {
                File imageFile = new File(imageObj.getPath());
                try {
                    image = ImageIO.read(imageFile);
                } catch (IOException e) {
                    throw new HbaseAccessException("path target is not exist>>>" + imageObj.getPath(), e);
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                ImageIO.write(image, imageObj.getExt(), outputStream);
            } catch (IOException e) {
                throw new HbaseAccessException(e.getMessage());
            }
            p.addColumn(HbaseTables.IMAGE_MOB, HbaseTables.IMAGE_MOB, outputStream.toByteArray());
        }*/

        return p;
    }

    private byte[] getRowKey(ImageObj imageObj) {
        if (imageObj.getExecutionId() != null &&
                imageObj.getImageId() != null) {
            String rowKey = imageObj.getExecutionId() + "-" + imageObj.getImageId();
            return Bytes.toBytes(rowKey);
        }
        long timestamp = imageObj.getCreateTime();
        long prefix = timestamp % ConstantUtil.NUM_BUCKETS;

        byte[] bytes = Bytes.toBytes(prefix);
        final long base_time;
        if ((timestamp & ConstantUtil.SECOND_MASK) != 0) {
            // drop the ms timestamp to seconds to calculate the base timestamp
            base_time = ((timestamp / 1000) -
                    ((timestamp / 1000) % ConstantUtil.MAX_TIMESPAN));
        } else {
            base_time = (timestamp - (timestamp % ConstantUtil.MAX_TIMESPAN));
        }
        return RowKeyUtils.concatFixedByteAndLong(bytes,bytes.length,base_time);
    }


}
