/*
 * Packages.java
 *
 * Copyright (C) 2002-2007 Peter Graves <peter@armedbear.org>
 * $Id: Packages.java 14570 2013-07-22 13:21:06Z mevenson $
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 */

package org.armedbear.lisp;

import java.util.List;

import static org.armedbear.lisp.Lisp.NIL;
import static org.armedbear.lisp.Lisp.getCurrentPackage;

public final class Packages
{
  public static final synchronized Package createPackage(String name)
  {
    return createPackage(name, 0);
  }

  public static final synchronized Package createPackage(String name, int size)
  {
    Symbol pkg = Symbol.TOP_LEVEL_PACKAGES.intern(name);
    return pkg.asPackage();
  }

  /**
      Returns the current package of the current LispThread.

      Intended to be used from Java code manipulating an Interpreter
      instance.
  */
  public static final synchronized Package findPackage(String name) {
    return getCurrentPackage().findPackage(name);
  }

  // Finds package named `name'.  Returns null if package doesn't exist.
  // Called by Package.findPackage after checking package-local package
  // nicknames.
  static final synchronized Package findPackageGlobally(String name)
  {
    Symbol pkg = Symbol.TOP_LEVEL_PACKAGES.findAccessibleSymbol(name);
    if(pkg == null || !pkg.isPackage()) {
      return null;
    }
    return pkg.asPackage();
  }

  public static final synchronized LispObject listAllTopLevelPackages()
  {
    LispObject result = NIL;
    for (Symbol pkg : Symbol.TOP_LEVEL_PACKAGES.getAccessibleSymbols()) {
      result = new Cons(pkg.asPackage(), result);
    }
    return result;
  }

  public static final synchronized Package[] getAllTopLevelPackages()
  {
    List<Symbol> symbols = Symbol.ROOT_SYMBOL.getAccessibleSymbols();
    Package[] array = new Package[symbols.size()];
    for(int i = 0; i < symbols.size(); i++) {
      array[i] = symbols.get(i).asPackage();
    }
    return array;
  }

  public static final synchronized LispObject getPackagesNicknamingPackage(Package thePackage)
  {
    LispObject result = NIL;
    for (Package pkg : getAllTopLevelPackages()) {
      for (Package nicknamedPackage : pkg.getLocallyNicknamedPackages()) {
        if (thePackage.equals(nicknamedPackage)) {
          result = new Cons(pkg, result);
        }
      }
    }
    return result;
  }
}
