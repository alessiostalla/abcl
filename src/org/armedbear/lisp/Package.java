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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.armedbear.lisp.Lisp.*;
import static org.armedbear.lisp.Symbol.SHADOWING_SYMBOLS;

public class Package extends LispObject implements java.io.Serializable {

  private Symbol symbol;
  private boolean deleted;
  private LispObject nicknames = NIL; //Note: during bootstrap, NIL is still null
  /** Symbols internal to the package. */
  protected transient final ConcurrentHashMap<String, Symbol> internalSymbols
          = new ConcurrentHashMap<String, Symbol>(16);
  /** Symbols exported from the package.
   *
   * Those symbols in this collection are not contained in the internalSymbols
   */
  protected transient final ConcurrentHashMap<String, Symbol> externalSymbols
          = new ConcurrentHashMap<String, Symbol>(16);

  private transient HashMap<String,Symbol> shadowingSymbols;
  private transient LispObject useList = null;
  private transient List<Package> usedByList = null;
  private transient ConcurrentHashMap<String, Package> localNicknames;

    // Anonymous package.
    public Package() {
      symbol = new Symbol("");
    }

    public Package(String name) {
      symbol = Symbol.TOP_LEVEL_PACKAGES.ensurePackage().intern(name);
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

    public final LispObject getNicknames() {
      return nicknames;
    }

    protected final void initNicknames() {
      if(nicknames != null) {
        throw new IntegrityError();
      }
      nicknames = NIL; //For bootstrap
    }

    public final synchronized boolean delete() {
        if(!deleted) {
          if(getUseList() instanceof Cons) {
            LispObject usedPackages = getUseList();
            while (usedPackages != NIL) {
              Package pkg = checkPackage(usedPackages.car());
              unusePackage(pkg);
              usedPackages = usedPackages.cdr();
            }
          }

          List<Package> usedByList = getUsedBy();
          if (usedByList != null) {
            while (!usedByList.isEmpty()) {
              usedByList.get(0).unusePackage(this);
            }
          }

          symbol.deletePackage();
          while (nicknames != NIL) {
            Symbol pkg = (Symbol) nicknames.car();
            pkg.deletePackage();
            nicknames = nicknames.cdr();
          }

          deleted = true;
          return true;
        }
        return false;
    }

  public final void rename(String newName, LispObject newNicks) {
    rename(Symbol.TOP_LEVEL_PACKAGES.ensurePackage().internAndExport(newName), newNicks);
  }

    public final synchronized void rename(Symbol newName, LispObject newNicks) {
      if(newName != symbol && newName.isPackage()) {
        error(new PackageError("Cannot rename package " + getName() + " to " + newName.getQualifiedName() + " as it is already a package", this));
        return;
      }
      delete();
      symbol = newName;
      newName.setPackageView(this);
      while(newNicks != NIL) {
        LispObject nick = newNicks.car();
        if(nick instanceof Symbol && ((Symbol) nick).getParent() != NIL) {
          addNickname((Symbol) nick);
        } else {
          addNickname(javaString(nick));
        }
        newNicks = newNicks.cdr();
      }
      deleted = false;
    }

  public Symbol findInternalSymbol(SimpleString name)
  {
    return internalSymbols.get(name.toString());
  }

  public Symbol findInternalSymbol(String name)
  {
    return internalSymbols.get(name);
  }

  public Symbol findExternalSymbol(SimpleString name)
  {
    return externalSymbols.get(name.toString());
  }

  public Symbol findExternalSymbol(String name)
  {
    return externalSymbols.get(name);
  }

  public Symbol findExternalSymbol(SimpleString name, int hash)
  {
    return externalSymbols.get(name.toString());
  }

  // Returns null if symbol is not accessible in this package.
  public Symbol findAccessibleSymbol(SimpleString name)

  {
    return findAccessibleSymbol(name.toString());
  }

  // Returns null if symbol is not accessible in this package.
  public Symbol findAccessibleSymbol(String name) {
    // Look in external and internal symbols of this package.
    Symbol symbol = externalSymbols.get(name);
    if (symbol != null)
      return symbol;
    symbol = internalSymbols.get(name);
    if (symbol != null)
      return symbol;
    // Look in external symbols of used packages.
    if (getUseList() instanceof Cons) {
      LispObject usedPackages = getUseList();
      while (usedPackages != NIL) {
        Package pkg = checkPackage(usedPackages.car());
        symbol = pkg.findExternalSymbol(name);
        if (symbol != null)
          return symbol;
        usedPackages = usedPackages.cdr();
      }
    }
    // Not found.
    return null;
  }

  public String getAccessibleName(Symbol symbol) {
    return getAccessibleName(symbol, new HashSet<Symbol>(), false);
  }

  //TODO ALessio does not work
  protected String getAccessibleName(Symbol symbol, Set<Symbol> alreadySeen, boolean external) {
    List<String> localNames = getLocalNames(symbol);
    if(external) {
      for(String localName : localNames) {
        if(externalSymbols.containsKey(localName)) {
          return localName;
        }
      }
    } else if(!localNames.isEmpty()) {
      return localNames.get(0);
    }
    // Look in external symbols of used packages.
    LispObject usedPackages = getUseList();
    if (usedPackages instanceof Cons) {
      while (usedPackages != NIL) {
        Package pkg = checkPackage(usedPackages.car());
        if(!alreadySeen.contains(pkg.getSymbol())) {
          alreadySeen.add(pkg.getSymbol());
          String accessibleName = pkg.getAccessibleName(symbol, alreadySeen, true);
          if(accessibleName != null) {
            Symbol other = internalSymbols.get(accessibleName);
            if(other == null || other == symbol) {
              return accessibleName;
            }
            other = externalSymbols.get(accessibleName);
            if(other == null || other == symbol) {
              return accessibleName;
            }
          }
        }
        usedPackages = usedPackages.cdr();
      }
    }
    // Not found.
    return null;
  }

  public LispObject findSymbol(String name)

  {
    final SimpleString s = new SimpleString(name);
    final LispThread thread = LispThread.currentThread();
    // Look in external and internal symbols of this package.
    Symbol symbol = externalSymbols.get(name);
    if (symbol != null)
      return thread.setValues(symbol, Keyword.EXTERNAL);
    symbol = internalSymbols.get(name);
    if (symbol != null)
      return thread.setValues(symbol, Keyword.INTERNAL);
    // Look in external symbols of used packages.
    if (getUseList() instanceof Cons) {
      LispObject usedPackages = getUseList();
      while (usedPackages != NIL) {
        Package pkg = checkPackage(usedPackages.car());
        symbol = pkg.findExternalSymbol(s);
        if (symbol != null)
          return thread.setValues(symbol, Keyword.INHERITED);
        usedPackages = usedPackages.cdr();
      }
    }
    // Not found.
    return thread.setValues(NIL, NIL);
  }

  // Helper function to add NIL to PACKAGE_CL.
  //TODO Alessio there must be another way...
  public void addSymbol(Symbol symbol)
  {
    Debug.assertTrue(symbol.getParent() == this.symbol);
    Debug.assertTrue(symbol.getName().equals("NIL"));
    externalSymbols.put(symbol.name.toString(), symbol);
  }

  protected Symbol addSymbol(String name) {
    Symbol symbol = new Symbol(name, this);
    internalSymbols.put(name, symbol);
    return symbol;
  }

  private Symbol addSymbol(SimpleString name)
  {
    return addSymbol(name.toString());
  }

  public Symbol addInternalSymbol(String symbolName)
  {
    final Symbol symbol = new Symbol(symbolName, this);
    internalSymbols.put(symbolName, symbol);
    return symbol;
  }

  public Symbol addExternalSymbol(String symbolName)
  {
    final Symbol symbol = new Symbol(symbolName, this);
    externalSymbols.put(symbolName, symbol);
    return symbol;
  }

  public synchronized Symbol intern(SimpleString symbolName)
  {
    return intern(symbolName.toString());
  }

  public synchronized Symbol intern(String symbolName)
  {
    // Look in external and internal symbols of this package.
    Symbol symbol = externalSymbols.get(symbolName);
    if (symbol != null)
      return symbol;
    symbol = internalSymbols.get(symbolName);
    if (symbol != null)
      return symbol;
    // Look in external symbols of used packages.
    if (getUseList() instanceof Cons) {
      LispObject usedPackages = getUseList();
      while (usedPackages != NIL) {
        Package pkg = checkPackage(usedPackages.car());
        symbol = pkg.externalSymbols.get(symbolName);
        if (symbol != null)
          return symbol;
        usedPackages = usedPackages.cdr();
      }
    }
    // Not found.
    return addSymbol(symbolName);
  }

  public synchronized Symbol intern(final SimpleString s,
                                    final LispThread thread)
  {
    // Look in external and internal symbols of this package.
    Symbol symbol = externalSymbols.get(s.toString());
    if (symbol != null)
      return (Symbol) thread.setValues(symbol, Keyword.EXTERNAL);
    symbol = internalSymbols.get(s.toString());
    if (symbol != null)
      return (Symbol) thread.setValues(symbol, Keyword.INTERNAL);
    // Look in external symbols of used packages.
    if (getUseList() instanceof Cons) {
      LispObject usedPackages = getUseList();
      while (usedPackages != NIL) {
        Package pkg = checkPackage(usedPackages.car());
        symbol = pkg.findExternalSymbol(s);
        if (symbol != null)
          return (Symbol) thread.setValues(symbol, Keyword.INHERITED);
        usedPackages = usedPackages.cdr();
      }
    }
    // Not found.
    return (Symbol) thread.setValues(addSymbol(s), NIL);
  }

  public synchronized Symbol internAndExport(String symbolName)

  {
    final SimpleString s = new SimpleString(symbolName);
    // Look in external and internal symbols of this package.
    Symbol symbol = externalSymbols.get(symbolName);
    if (symbol != null)
      return symbol;
    symbol = internalSymbols.get(symbolName);
    if (symbol != null) {
      export(symbol);
      return symbol;
    }
    if (getUseList() instanceof Cons) {
      // Look in external symbols of used packages.
      LispObject usedPackages = getUseList();
      while (usedPackages != NIL) {
        Package pkg = checkPackage(usedPackages.car());
        symbol = pkg.findExternalSymbol(s);
        if (symbol != null) {
          export(symbol);
          return symbol;
        }
        usedPackages = usedPackages.cdr();
      }
    }
    // Not found.
    symbol = addSymbol(s);
    internalSymbols.remove(symbolName);
    externalSymbols.put(symbolName, symbol);
    return symbol;
  }

  public synchronized LispObject unintern(final Symbol symbol) {
    List<String> symbolNames = getLocalNames(symbol);
    boolean found = canUnintern(symbol, symbolNames);
    if (!found) {
      return NIL;
    }

    unintern(symbol, symbolNames);
    return T;
  }

  protected boolean canUnintern(Symbol symbol) {
    return canUnintern(symbol, getLocalNames(symbol));
  }

  protected boolean canUnintern(Symbol symbol, List<String> symbolNames) {
    boolean found = false;
    for (String symbolName : symbolNames) {
      if (canUnintern(symbol, symbolName)) {
        found = true;
      }
    }
    return found;
  }

  protected boolean canUnintern(Symbol symbol, String symbolName) {
    if (shadowingSymbols != null && shadowingSymbols.get(symbolName) == symbol) {
      // Check for conflicts that might be exposed in used package list
      // if we remove the shadowing symbol.
      Symbol sym = null;
      if (getUseList() instanceof Cons) {
        LispObject usedPackages = getUseList();
        while (usedPackages != NIL) {
          Package pkg = checkPackage(usedPackages.car());
          Symbol s = pkg.findExternalSymbol(symbol.name);
          if (s != null) {
            if (sym == null)
              sym = s;
            else if (sym != s) {
              StringBuilder sb =
                      new StringBuilder("Uninterning the symbol ");
              sb.append(symbol.getQualifiedName());
              sb.append(" causes a name conflict between ");
              sb.append(sym.getQualifiedName());
              sb.append(" and ");
              sb.append(s.getQualifiedName());
              error(new PackageError(sb.toString(), this));
              return false;
            }
          }
          usedPackages = usedPackages.cdr();
        }
      }
    }
    if (externalSymbols.get(symbolName) == symbol) {
      return true;
    }
    if (internalSymbols.get(symbolName) == symbol) {
      return true;
    }
    return false;
  }

  protected synchronized void unintern(Symbol symbol, List<String> symbolNames) {
    internalSymbols.keySet().removeAll(symbolNames);
    externalSymbols.keySet().removeAll(symbolNames);
    if(shadowingSymbols != null) {
      shadowingSymbols.keySet().removeAll(symbolNames);
    }

    if (symbol.getParent() == this.symbol) {
      symbol.setParent(NIL);
    }
  }

  /**
   * Returns the list of local names of symbol in this namespace.
   * @param symbol
   * @return
   */
  public List<String> getLocalNames(Symbol symbol) {
    List<String> symbolNames = new ArrayList<String>();
    if (findAccessibleSymbol(symbol.getName()) == symbol) {
      symbolNames.add(symbol.getName());
    }

        /*        Map<Symbol, List<String>> aliases = symbol.getAliases();
        if (aliases != null) {
            List<String> names = aliases.get(this);
            if(names != null) {
                for (String alias : names) {
                    symbolNames.add(alias);
                }
            }
            }*/
    return symbolNames;
  }

  public synchronized void importSymbol(Symbol symbol) {
    Symbol sym = findAccessibleSymbol(symbol.name);
    if (sym != null && sym != symbol) {
      StringBuilder sb = new StringBuilder("The symbol ");
      sb.append(symbol.name);
      sb.append(", or ");
      sb.append(sym.getQualifiedName());
      sb.append(", is already accessible in package ");
      sb.append(this.symbol.getQualifiedName());
      sb.append('.');
      error(new PackageError(sb.toString(), this));
    }
    internalSymbols.put(symbol.name.toString(), symbol);
    if (symbol.getParent() == NIL) {
      symbol.setParent(this.symbol);
    }
  }

  public synchronized void export(final Symbol symbol)
  {
    export(symbol, null);
  }

  protected void export(Symbol symbol, String symbolName) {
    List<String> localNames = getLocalNames(symbol);
    if(localNames.isEmpty() || (symbolName != null && !localNames.contains(symbolName))) {
      StringBuilder sb = new StringBuilder("The symbol ");
      sb.append(symbol.getQualifiedName());
      sb.append(" is not accessible in package ");
      sb.append(symbol.name);
      sb.append(" as ");
      sb.append(symbolName);
      sb.append('.');
      error(new PackageError(sb.toString(), this));
      return;
    }
    if(symbolName == null) {
      symbolName = localNames.get(0);
    }
    boolean added = false;
    if (symbol.getParent() != this.symbol) {
      Symbol sym = findAccessibleSymbol(symbolName);
      if (sym != symbol) {
        StringBuilder sb = new StringBuilder("The symbol ");
        sb.append(symbol.getQualifiedName());
        sb.append(" is not accessible in package ");
        sb.append(symbol.name);
        sb.append('.');
        error(new PackageError(sb.toString(), this));
        return;
      }
      internalSymbols.put(symbolName, symbol);
      added = true;
    }
    if (added || internalSymbols.get(symbolName.toString()) == symbol) {
      if (getUsedBy() != null) {
        for (Package pkg : getUsedBy()) {
          Symbol sym = pkg.findAccessibleSymbol(symbolName);
          if (sym != null && sym != symbol) {
            if (shadowingSymbols != null && shadowingSymbols.get(symbolName) == sym) {
              // OK.
            } else {
              StringBuilder sb = new StringBuilder("The symbol ");
              sb.append(sym.getQualifiedName());
              sb.append(" is already accessible in package ");
              sb.append(pkg.getName());
              sb.append('.');
              error(new PackageError(sb.toString(), pkg));
              return;
            }
          }
        }
      }
      // No conflicts.
      internalSymbols.remove(symbolName.toString());
      externalSymbols.put(symbolName.toString(), symbol);
      return;
    }
    if (externalSymbols.get(symbolName.toString()) == symbol)
      // Symbol is already exported; there's nothing to do.
      return;
    StringBuilder sb = new StringBuilder("The symbol ");
    sb.append(symbol.getQualifiedName());
    sb.append(" is not accessible in package ");
    sb.append(symbol.name);
    sb.append('.');
    error(new PackageError(sb.toString(), this));
  }

  public synchronized void unexport(final Symbol symbol)

  {
    if (externalSymbols.get(symbol.name.toString()) == symbol) {
      externalSymbols.remove(symbol.name.toString());
      internalSymbols.put(symbol.name.toString(), symbol);
    } else if (findAccessibleSymbol(symbol.name.toString()) != symbol) {
      StringBuilder sb = new StringBuilder("The symbol ");
      sb.append(symbol.getQualifiedName());
      sb.append(" is not accessible in package ");
      sb.append(getName());
      error(new PackageError(sb.toString(), this));
    }
  }

  public synchronized void shadow(final String symbolName)

  {
    if (shadowingSymbols == null) {
      shadowingSymbols = new HashMap<String, Symbol>();
    }
    final SimpleString s = new SimpleString(symbolName);
    Symbol symbol = externalSymbols.get(s.toString());
    if (symbol != null) {
      shadowingSymbols.put(symbolName, symbol);
      return;
    }
    symbol = internalSymbols.get(s.toString());
    if (symbol != null) {
      shadowingSymbols.put(symbolName, symbol);
      return;
    }
    if (shadowingSymbols.get(symbolName) != null)
      return;
    symbol = new Symbol(s, this);
    internalSymbols.put(s.toString(), symbol);
    shadowingSymbols.put(symbolName, symbol);
  }

  protected static Symbol checkExistingSymbol(Symbol symbol, String name, ConcurrentHashMap<String, Symbol> internalSymbols) {
    Symbol other = internalSymbols.get(name);
    if(other != null) {
      if (other == symbol) {
        return symbol;
      } else {
        error(new PackageError("A symbol named " + name + " already exists.", new SimpleString(name))); //TODO Alessio distinguish alias package from alias symbol
        return NIL;
      }
    }
    return null;
  }

  public synchronized void shadowingImport(Symbol symbol)
  {
    final String symbolName = symbol.getName();
    Symbol sym = externalSymbols.get(symbolName);
    if (sym == null)
      sym = internalSymbols.get(symbol.name.toString());

    // if a different symbol with the same name is accessible,
    // [..] which implies that it must be uninterned if it was present
    if (sym != null && sym != symbol) {
      if (shadowingSymbols != null)
        shadowingSymbols.remove(symbolName);
      unintern(sym);
    }

    if (sym == null || sym != symbol) {
      // there was no symbol, or we just unintered it another one
      // intern the new one
      internalSymbols.put(symbol.name.toString(), symbol);
    }

    if (shadowingSymbols == null) {
      shadowingSymbols = new HashMap<String, Symbol>();
    }
    shadowingSymbols.put(symbolName, symbol);
  }

  // "USE-PACKAGE causes PACKAGE to inherit all the external symbols of
  // PACKAGES-TO-USE. The inherited symbols become accessible as internal
  // symbols of PACKAGE."
  public void usePackage(Package pkg)
  {
    if (!memq(pkg, getUseList())) {
      // "USE-PACKAGE checks for name conflicts between the newly
      // imported symbols and those already accessible in package."
      Collection<Symbol> symbols = pkg.getExternalSymbols(); //TODO alessio should be a map!
      for (Symbol symbol : symbols) {
        Symbol existing = findAccessibleSymbol(symbol.name);
        if (existing != null && existing != symbol) {
          if (shadowingSymbols == null ||
                  shadowingSymbols.get(symbol.getName()) == null) {
            error(new PackageError("A symbol named " + symbol.getName() +
                    " is already accessible in package " +
                    getName() + ".", this));
            return;
          }
        }
      }
      setUseList(getUseList().push(pkg));
      // Add this package to the used-by list of pkg.
      if (pkg.getUsedBy() != null) {
        Debug.assertTrue(!pkg.getUsedBy().contains(this));
      } else {
        pkg.setUsedBy(new ArrayList<Package>());
      }
      pkg.getUsedBy().add(this);
    }
  }

  public void unusePackage(Package pkg)
  {
    LispObject useList = getUseList();
    if (useList instanceof Cons) {
      if (memq(pkg, useList)) {
        // FIXME Modify the original list instead of copying it!
        LispObject newList = NIL;
        while (useList != NIL) {
          if (useList.car() != pkg)
            newList = newList.push(useList.car());
          useList = useList.cdr();
        }
        useList = newList.nreverse();
        setUseList(useList);
        Debug.assertTrue(!memq(pkg, useList));
        Debug.assertTrue(pkg.getUsedBy() != null);
        Debug.assertTrue(pkg.getUsedBy().contains(this));
        pkg.getUsedBy().remove(this);
      }
    }
  }

  public LispObject getUseList()
  {
    return useList != null ? useList : NIL;
  }

  public LispObject setUseList(LispObject useList) {
    return this.useList = useList;
  }

  public boolean uses(LispObject pkg)
  {
    return (getUseList() instanceof Cons) && memq(pkg, getUseList());
  }

  protected List<Package> getUsedBy() {
    return usedByList;
  }

  protected void setUsedBy(List<Package> usedByList) {
    this.usedByList = usedByList;
  }

  public LispObject getUsedByList()
  {
    LispObject list = NIL;
    if (getUsedBy() != null) {
      for (Iterator it = getUsedBy().iterator(); it.hasNext();) {
        Symbol pkg = (Symbol) it.next();
        list = new Cons(pkg.ensurePackage(), list);
      }
    }
    return list;
  }

  public LispObject getLocalPackageNicknames() {
    LispObject list = NIL;
    for(Map.Entry<String, Symbol> entry : internalSymbols.entrySet()) {
      Symbol symbol = entry.getValue();
      if(symbol.isPackage() && !entry.getKey().equals(symbol.getName())) {
        list = new Cons(new Cons(entry.getKey(), symbol), list);
      }
    }
    for (Map.Entry<String, Symbol> entry : externalSymbols.entrySet()) {
      Symbol symbol = entry.getValue();
      if (symbol.isPackage() && !entry.getKey().equals(symbol.getName())) {
        list = new Cons(new Cons(entry.getKey(), symbol), list);
      }
    }
    return list;
  }


  public LispObject getShadowingSymbols()
  {
    LispObject list = NIL;
    if (shadowingSymbols != null) {
      for (Symbol symbol : shadowingSymbols.values()) {
        list = new Cons(symbol, list);
      }
    }
    return list;
  }

  public synchronized Collection<Symbol> getExternalSymbols()
  {
    return externalSymbols.values();
  }

  public synchronized List<Symbol> getAccessibleSymbols()
  {
    ArrayList<Symbol> list = new ArrayList<Symbol>();
    list.addAll(internalSymbols.values());
    list.addAll(externalSymbols.values());
    if (getUseList() instanceof Cons) {
      LispObject usedPackages = getUseList();
      while (usedPackages != NIL) {
        Package pkg = checkPackage(usedPackages.car());
        list.addAll(pkg.externalSymbols.values());

        usedPackages = usedPackages.cdr();
      }
    }
    return list;
  }

  public synchronized LispObject PACKAGE_INTERNAL_SYMBOLS()
  {
    LispObject list = NIL;
    Collection<Symbol> symbols = internalSymbols.values();
    for (Symbol symbol : symbols) {
      list = new Cons(symbol, list);
    }
    return list;
  }

  public synchronized LispObject PACKAGE_EXTERNAL_SYMBOLS()
  {
    LispObject list = NIL;
    Collection<Symbol> symbols = externalSymbols.values();
    for (Symbol symbol : symbols) {
      list = new Cons(symbol, list);
    }
    return list;
  }

  public synchronized LispObject PACKAGE_INHERITED_SYMBOLS()
  {
    LispObject list = NIL;
    if (getUseList() instanceof Cons) {
      LispObject usedPackages = getUseList();
      while (usedPackages != NIL) {
        Package pkg = (Package) usedPackages.car();
        Collection<Symbol> externals = pkg.getExternalSymbols();
        for (Symbol symbol : externals) {
          if (shadowingSymbols != null && shadowingSymbols.get(symbol.getName()) != null)
            continue;
          if (externalSymbols.get(symbol.name.toString()) == symbol)
            continue;
          list = new Cons(symbol, list);
        }
        usedPackages = usedPackages.cdr();
      }
    }
    return list;
  }

  public synchronized LispObject getSymbols()
  {
    LispObject list = NIL;
    Collection<Symbol> internals = internalSymbols.values();
    for (Symbol internal : internals) {
      list = new Cons(internal, list);
    }
    Collection<Symbol> externals = externalSymbols.values();
    for (Symbol external : externals) {
      list = new Cons(external, list);
    }
    return list;
  }

  public synchronized Symbol[] symbols()
  {
    Collection<Symbol> internals = internalSymbols.values();
    Collection<Symbol> externals = externalSymbols.values();
    Symbol[] array = new Symbol[internals.size() + externals.size()];
    int i = 0;
    for (Symbol symbol : internals) {
      array[i++] = symbol;
    }
    for (Symbol symbol : externals) {
      array[i++] = symbol;
    }
    return array;
  }

  public final void addNickname(String s) {
    addNickname(Symbol.TOP_LEVEL_PACKAGES.ensurePackage().internAndExport(s));
  }

    public final void addNickname(Symbol s)
    {
      if(s.isPackage()) {
        error(new PackageError("A package named " + s.name + " already exists.", s.name));
      } else {
        s.setPackageView(this);
        nicknames = new Cons(s, nicknames);
      }
    }

    public Symbol getNickname() {
      return checkSymbol(nicknames.car());
    }

    public LispObject packageNicknames() {
      return nicknames;
    }

  public LispObject addLocalPackageNickname(String name, Package pack)
  {
    return NIL; //TODO
  }

  public LispObject removeLocalPackageNickname(String name)
  {
    return NIL; //TODO
  }

  public void removeLocalPackageNicknamesForPackage(Package p)
  {
    //TODO
  }

    public Collection<Package> getLocallyNicknamedPackages() {
      //TODO
      return null;
    }

    public Package findPackage(String name) {
        // Find package named `name', taking local nicknames into account
        Symbol symbol = findAccessibleSymbol(name);
        if (symbol != null && symbol.isPackage()) {
            return symbol.ensurePackage();
        }
        return Packages.findPackageGlobally(name);
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
