Instant Adapter
----------------
Just like instant coffee, saves 78% of your time on Android's Custom Adapters.

Quick Start
-----------
**Step 1 - Annotate methods in your model**
```java
class Book {
    ...

    @InstantText(viewId = R.id.title)
    public String getTitle() {
         return title;
    }

    @InstantText(viewId = R.id.author, formatString = "Author: %s")
    public String getAuthor() {
         return author;
    }
}
```

**Step 2 - Initialize and set an Instant Adapter to your ListView**
```java
InstantAdapter<Book> bookAdapter = new InstantAdapter<Book>(context, R.layout.book_item, Book.class, books);
bookListView.setAdapter(bookAdapter);
```

License
---------------------

    Copyright 2013 Mobs and Geeks

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
