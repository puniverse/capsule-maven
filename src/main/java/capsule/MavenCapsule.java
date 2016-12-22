/*
 * Capsule
 * Copyright (c) 2014-2016, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author pron
 */
public interface MavenCapsule {
    static final int LOG_NONE1 = 0;
    static final int LOG_QUIET1 = 1;
    static final int LOG_VERBOSE1 = 2;
    static final int LOG_DEBUG1 = 3;
    
    List<Path> lookupAndResolve(String x, String type);
    
    boolean isLogging1(int level);

    void log1(int level, String str);

    void log1(int level, Throwable t);
}
