/*
 * Package.java
 *
 * Copyright (C) 2002-2007 Peter Graves <peter@armedbear.org>
 * $Id: Package.java 14570 2013-07-22 13:21:06Z mevenson $
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.armedbear.lisp.Lisp.*;

public class Package extends LispObject implements java.io.Serializable {

  private Symbol symbol;
    private boolean deleted;

    // Anonymous package.
    public Package() {
      symbol = new Symbol("");
    }

    public Package(String name) {
      symbol = Symbol.TOP_LEVEL_PACKAGES.intern(name);
    }

  public Package(Symbol symbol) {
    if(symbol == null)
      throw new NullPointerException("symbol must not be null");
    this.symbol = symbol;
  }

    @Override
    public LispObject typeOf()
    {
        return Symbol.PACKAGE;
    }

    @Override
    public LispObject classOf()
    {
        return BuiltInClass.PACKAGE;
    }

    @Override
    public LispObject getDescription()
    {
      StringBuilder sb = new StringBuilder("The ");
      sb.append(getName());
      sb.append(" package");
      return new SimpleString(sb);
    }

    @Override
    public LispObject typep(LispObject type)
    {
        if (type == Symbol.PACKAGE)
            return T;
        if (type == BuiltInClass.PACKAGE)
            return T;
        return super.typep(type);
    }

    public String getName()
    {
      return symbol.getName();
    }

    public LispObject NAME() {
        return deleted ? NIL : symbol.getSymbolName();
    }

    @Override
    public final LispObject getPropertyList()
    {
      return symbol.getPropertyList();
    }

    @Override
    public final void setPropertyList(LispObject obj)
    {
      symbol.setPropertyList(obj);
    }

    public final List<String> getNicknames() {
      List<String> localNames = Symbol.TOP_LEVEL_PACKAGES.getLocalNames(getSymbol());
        localNames.remove(getName());
        return localNames;
    }

    public final synchronized boolean delete() {
        if(!deleted) {
            symbol.delete();
            deleted = true;
            return true;
        }
        return false;
    }

    public final synchronized void rename(String newName, LispObject newNicks) {
      symbol.rename(newName, newNicks);
    }

    public Symbol findInternalSymbol(SimpleString name)
    {
      return symbol.findInternalSymbol(name);
    }

    public Symbol findInternalSymbol(String name)
    {
      return symbol.findInternalSymbol(name);
    }

    public Symbol findExternalSymbol(SimpleString name)
    {
      return symbol.findExternalSymbol(name);
    }

    public Symbol findExternalSymbol(String name)
    {
      return symbol.findExternalSymbol(name);
    }

    public Symbol findExternalSymbol(SimpleString name, int hash)
    {
      return symbol.findExternalSymbol(name, hash);
    }

    // Returns null if symbol is not accessible in this package.
    public Symbol findAccessibleSymbol(String name)

    {
        return symbol.findAccessibleSymbol(name);
    }

    // Returns null if symbol is not accessible in this package.
    public Symbol findAccessibleSymbol(SimpleString name) {
      return symbol.findAccessibleSymbol(name);
    }

    public LispObject findSymbol(String name) {
      return symbol.findSymbol(name);
    }

    // Helper function to add NIL to PACKAGE_CL.
    public void addSymbol(Symbol symbol)
    {
      this.symbol.addSymbol(symbol);
    }

    public Symbol addInternalSymbol(String symbolName)
    {
      return symbol.addInternalSymbol(symbolName);
    }

    public Symbol addExternalSymbol(String symbolName)
    {
      return symbol.addExternalSymbol(symbolName);
    }

    public synchronized Symbol intern(SimpleString symbolName)
    {
        return symbol.intern(symbolName);
    }

    public synchronized Symbol intern(String symbolName)
    {
      return symbol.intern(symbolName);
    }

    public synchronized Symbol intern(final SimpleString s,
                                      final LispThread thread)
    {
      return symbol.intern(s, thread);
    }

    public synchronized Symbol internAndExport(String symbolName)

    {
      return symbol.internAndExport(symbolName);
    }

    public synchronized LispObject unintern(final Symbol symbol) {
      return this.symbol.unintern(symbol);
    }

    public synchronized void importSymbol(Symbol symbol)
    {
      this.symbol.importSymbol(symbol);
    }

    public synchronized void export(final Symbol symbol)
    {
      this.symbol.export(symbol);
    }

    public synchronized void unexport(final Symbol symbol)

    {
      this.symbol.unexport(symbol);
    }

    public synchronized void shadow(final String symbolName)

    {
      symbol.shadow(symbolName);
    }

    public synchronized void shadowingImport(Symbol symbol)
    {
      this.symbol.shadowingImport(symbol);
    }

    public void usePackage(Package pkg)
    {
      symbol.useNamespace(pkg.getSymbol());
    }

    public void unusePackage(Package pkg)
    {
      symbol.unuseNamespace(pkg.getSymbol());
    }

    public final void addNickname(String s)
    {
      symbol.addNickname(s);
    }

    public String getNickname() {
        List<String> nicknames = getNicknames();
        if (!nicknames.isEmpty()) {
            return nicknames.get(0);
        } else {
            return null;
        }
    }

    public LispObject packageNicknames() {
        LispObject list = NIL;
        List<String> nicknames = getNicknames();
        if (nicknames != null) {
            for (int i = nicknames.size(); i-- > 0; ) {
                String nickname = nicknames.get(i);
                list = new Cons(new SimpleString(nickname), list);
            }
        }
        return list;
    }

    public LispObject getUseList() {
        LispObject useList = symbol.getUseList();
        if(useList != NIL) {
            LispObject newUseList = NIL;
            while (useList != NIL) {
                newUseList = newUseList.push(checkSymbol(useList.car()).asPackage());
                useList = useList.cdr();
            }
            useList = newUseList.nreverse();
        }
        return useList;
    }

    public boolean uses(LispObject pkg)
    {
      return symbol.uses(pkg);
    }

    public LispObject getUsedByList()
    {
      return symbol.getUsedByList();
    }

  public LispObject getLocalPackageNicknames()
  {
    return symbol.getLocalPackageNicknames();
  }

  public LispObject addLocalPackageNickname(String name, Package pack)
  {
    return symbol.addLocalPackageNickname(name, pack.getSymbol());
  }

  public LispObject removeLocalPackageNickname(String name)
  {
    return symbol.removeLocalPackageNickname(name);
  }

  public void removeLocalPackageNicknamesForPackage(Package p)
  {
    symbol.removeLocalPackageNicknamesForPackage(p.getSymbol());
  }

    public Collection<Package> getLocallyNicknamedPackages() {
        Collection<Symbol> nicknames = symbol.getAliasedSymbols();
        List<Package> result = new ArrayList<Package>();
        for (Symbol s : nicknames) {
            if (s.isPackage()) {
                result.add(s.asPackage());
            }
        }
        return result;
    }

    public Package findPackage(String name) {
        // Find package named `name', taking local nicknames into account
        Symbol symbol = getSymbol().findAccessibleSymbol(name);
        if (symbol != null && symbol.isPackage()) {
            return symbol.asPackage();
        }
        return Packages.findPackageGlobally(name);
    }

    public LispObject getShadowingSymbols()
    {
      return symbol.getShadowingSymbols();
    }

    public synchronized Collection getExternalSymbols()
    {
      return symbol.getExternalSymbols();
    }

    public synchronized List<Symbol> getAccessibleSymbols()
    {
      return symbol.getAccessibleSymbols();
    }

    public synchronized LispObject PACKAGE_INTERNAL_SYMBOLS()
    {
      return symbol.PACKAGE_INTERNAL_SYMBOLS();
    }

    public synchronized LispObject PACKAGE_EXTERNAL_SYMBOLS()
    {
      return symbol.PACKAGE_EXTERNAL_SYMBOLS();
    }

    public synchronized LispObject PACKAGE_INHERITED_SYMBOLS()
    {
      return symbol.PACKAGE_INHERITED_SYMBOLS();
    }

    public synchronized LispObject getSymbols()
    {
      return symbol.getSymbols();
    }

    public synchronized Symbol[] symbols()
    {
      return symbol.symbols();
    }

    @Override
    public String printObject()
    {
      if (_PRINT_FASL_.symbolValue() != NIL) {
            StringBuilder sb = new StringBuilder("#.(CL:FIND-PACKAGE \"");
            sb.append(getName());
            sb.append("\")");
            return sb.toString();
        } else {
          return unreadableString("PACKAGE " + getName(), false);
      }
    }

  public Symbol getSymbol() {
    return symbol;
  }

  public boolean equal(LispObject obj)
  {
    return equals(obj);
  }

  public boolean equalp(LispObject obj)
  {
    return equals(obj);
  }

  public boolean equals(Object obj) {
    return obj instanceof Package && ((Package) obj).symbol == symbol;
  }

    public Object readResolve() throws java.io.ObjectStreamException {
        String name = getName();
        Package pkg = findPackage(name);
        if(pkg != null) {
            return pkg;
        } else {
            return error(new PackageError(name + " is not the name of a package.", new SimpleString(name)));
        }
    }
}
