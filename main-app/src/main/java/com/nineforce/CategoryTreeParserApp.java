package com.nineforce;

/*
  Testing the CategoryTreeParserService

  ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none"\
  -Dspring-boot.run.main-class=com.nineforce.CategoryTreeParserApp



    or  If you’d rather not invoke the Spring‑Boot plugin:
    ./mvnw clean compile exec:java \
    -Dexec.mainClass=com.nineforce.CategoryTreeParserApp \
    -Dspring.profiles.active=local
 */

import com.nineforce.service.product.category.CategoryTreeDBPrinter;
import com.nineforce.service.product.category.CategoryTreeParserService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

//@SpringBootApplication(scanBasePackages = "com.nineforce")
public class CategoryTreeParserApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(CategoryTreeParserApp.class, args);
        //CategoryTreeParserService parser =
                ctx.getBean(CategoryTreeParserService.class);

        try {
            //Map<Map.Entry<String, String>, Map<Map.Entry<String, String>, Set<Map.Entry<String, String>>>> categoryTree;
            //categoryTree = parser.parseCategoryTree_Simple(CategoryTreeParserService.FILE_NAME);
            //parser.printCategoryTree(categoryTree);

            //parser.importAndPersist(CategoryTreeParserService.FILE_NAME);
            //parser.printCategoryTree();
            // 2) print in‑memory tree (optional)
            // parser.printCategoryTree(parser.parseCategoryTree_Simple(CategoryTreeParserService.FILE_NAME));

            // 3) print the *persisted* tree from the database
            CategoryTreeDBPrinter printer = ctx.getBean(CategoryTreeDBPrinter.class);
            String tree = printer.buildTreeString();
            System.out.println(tree);

        } catch (Exception e) {
            System.err.println("Import failed: " + e.getMessage());
        }

        SpringApplication.exit(ctx, () -> 0);
    }
}

