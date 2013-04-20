/*
 * @(#)BlobWriterReader.java     2 Nov 2011
 *
 * Copyright © 2010 Andrew Phillips.
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package org.jclouds.labs.blobstore.exercise2;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.logging.log4j.config.Log4JLoggingModule;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

/**
 * @author aphillips
 * @since 2 Nov 2011
 *
 */
public class MultiFileUploaderB {
    private static final String RESOURCE_DIR = "src/main/resources";
    
    private final BlobStoreContext ctx;
    private final ExecutorService executor;
    
    public MultiFileUploaderB(String provider, String identity, String credential) {
        ctx = ContextBuilder.newBuilder(provider).credentials(identity, credential)
              .modules(ImmutableSet.of(new Log4JLoggingModule())).buildView(BlobStoreContext.class);
        executor = Executors.newCachedThreadPool(); // many other options available here
    }
    
    public void uploadFiles(List<File> files) {
        final BlobStore store = ctx.getBlobStore();
        final String containerName = "test-container-2";
        long startTimeMillis = System.currentTimeMillis();
        int numFiles = files.size();
        final CountDownLatch latch = new CountDownLatch(numFiles);
        System.out.format("Starting upload of %d files%n", numFiles);
        for (File file : files) {
            String filename = file.getName();
            final Blob blob = store.blobBuilder(filename).payload(file).build();
            System.out.format("Uploading '%s'...%n", filename);
            executor.submit(new Runnable() {
                  @Override
                  public void run() {
                     store.putBlob(containerName, blob);
                     System.out.println("Upload of a file completed");
                     latch.countDown();
                  }
               });
        }
        try {
            latch.await();
            System.out.format("Uploaded %d files in %dms%n", numFiles,
                    System.currentTimeMillis() - startTimeMillis);
        } catch (InterruptedException exception) {
            System.err.println("Interrupted whilst waiting for uploads to complete");
        } finally {
            tryDeleteContainer(store, containerName);
        }
    }

    private static void tryDeleteContainer(BlobStore store, String containerName) {
        try {
            store.deleteContainer(containerName);
        } catch (RuntimeException exception) {
            System.err.format("Unable to delete container due to: %s%n", exception.getMessage());
        }
    }
    
    public void cleanup() {
        ctx.close();
    }
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.format("%nUsage: %s <provider> <identity> <credential>%n", MultiFileUploaderB.class.getSimpleName());
            System.exit(1);
        }
        MultiFileUploaderB uploader = new MultiFileUploaderB(args[0], args[1], args[2]);
        try {
            uploader.uploadFiles(toFilesInResources("s3-api.pdf", "s3-dg.pdf", "s3-gsg.pdf",
                    "s3-qrc.pdf", "s3-ug.pdf"));
        } finally {
            uploader.cleanup();
        }
    }
    
    private static List<File> toFilesInResources(String... filenames) {
        return Lists.transform(Arrays.asList(filenames), new Function<String, File>() {
                @Override
                public File apply(String input) {
                    return new File(RESOURCE_DIR + File.separator + input);
                }
            });
    }
}
