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

import static com.google.common.collect.Lists.transform;
import static java.util.concurrent.Executors.newCachedThreadPool;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.logging.log4j.config.Log4JLoggingModule;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * @author aphillips
 * @since 2 Nov 2011
 *
 */
public class MultiFileUploaderC {
    private static final String RESOURCE_DIR = "src/main/resources";
    
    private final BlobStoreContext ctx;
    private final ListeningExecutorService executor;
    
    public MultiFileUploaderC(String provider, String identity, String credential) {
        ctx = ContextBuilder.newBuilder(provider).credentials(identity, credential)
              .modules(ImmutableSet.of(new Log4JLoggingModule())).buildView(BlobStoreContext.class);
        executor = MoreExecutors.listeningDecorator(newCachedThreadPool()); // many other options available here
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" }) // List cast guaranteed by invokeAll contract
    public void uploadFiles(List<File> files) {
        final BlobStore store = ctx.getBlobStore();
        final String containerName = "test-container-2";
        List<Callable<String>> putBlobRequests = transform(files, new Function<File, Callable<String>>() {
                @Override
                public Callable<String> apply(File input) {
                    return lazyPutFileBlob(input, store, containerName);
                }
            });
        long startTimeMillis = System.currentTimeMillis();
        System.out.format("Starting upload of %d files%n", files.size());
        try {
           List<String> etags = Futures.<String>successfulAsList((List) executor.invokeAll(putBlobRequests)).get();
           System.out.format("Uploaded %d files in %dms%n", files.size(),
                 System.currentTimeMillis() - startTimeMillis);
           for (int i = 0; i < files.size(); i++) {
              System.out.format("ETag for '%s': %s%n", files.get(i).getName(),
                    Optional.fromNullable(etags.get(i)).or("<upload error>"));
           }
        } catch (InterruptedException exception) {
           System.err.println("Interrupted whilst waiting for uploads to complete");
        } catch (ExecutionException exception) {
           System.err.println("Failed to retrieve ETags");
        } finally {
           tryDeleteContainer(ctx.getBlobStore(), containerName);
        }
    }
    
    private static Callable<String> lazyPutFileBlob(final File file,
          final BlobStore store, final String containerName) {
        return new Callable<String>() {
              @Override
              public String call() {
                  String filename = file.getName();
                  System.out.format("Uploading '%s'...%n", filename);
                  return store.putBlob(containerName,
                        store.blobBuilder(filename).payload(file).build());
              }
          };
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
            System.out.format("%nUsage: %s <provider> <identity> <credential>%n", MultiFileUploaderC.class.getSimpleName());
            System.exit(1);
        }
        MultiFileUploaderC uploader = new MultiFileUploaderC(args[0], args[1], args[2]);
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
